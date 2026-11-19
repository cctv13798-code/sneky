package com.example.ykens;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.SignBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Ykens: görünmeyen bir varlık. Gerçek bir entity yok; her şey oyuncunun etrafında
 * sunucu tarafında "musallat" olaylarıyla simüle edilir.
 *
 * Aşama 1: arkadan sesler / ayak sesleri, sandık karıştırma
 * Aşama 2: + köylüler kaybolur, ani yağmur
 * Aşama 3: + golemler saldırır, evine dönünce tabela, 1000 blok yürüyünce lav tuzağı
 */
public final class HauntManager {
	private HauntManager() {}

	// ---- Ayarlar (1 saniye = 20 tick) ----
	static final int AUTO_START_TICKS = 20 * 60 * 5;   // oturum başladıktan 5 dk sonra kendiliğinden başlar
	static final int STAGE_UP_TICKS = 20 * 60 * 6;     // her 6 dk'da bir sonraki aşama (en fazla 3)
	static final int WALK_DISTANCE = 1000;             // tabeladaki "yürü" mesafesi
	static final int HOME_RADIUS = 12;                 // "eve döndü" sayılan yarıçap
	static final int AWAY_RADIUS = 48;                 // "evden uzaklaştı" sayılan yarıçap

	static final boolean SHUFFLE_CHESTS = true;
	static final boolean REMOVE_VILLAGERS = true;      // DİKKAT: köylüleri gerçekten siler
	static final boolean CHANGE_WEATHER = true;
	static final boolean GOLEMS_TURN_HOSTILE = true;
	static final boolean LAVA_TRAP = true;             // DİKKAT: gerçek lav bloğu koyar

	private static final Map<UUID, State> STATES = new HashMap<>();
	private static final Random RNG = new Random();

	// ---- Tabela metinleri (satır başına en fazla ~15 karakter) ----
	private static final String[] SIGN_TRUST = {"Bana güven.", "Evinden çık,", "sonra 1000 blok", "dümdüz yürü."};
	private static final String[] SIGN_LAVA = {"Geldin.", "Şimdi", "lava'ya atla.", "- YKENS"};
	private static final String[][] SIGN_WHISPERS = {
			{"Arkana bakma."},
			{"Seni görüyorum."},
			{"Yalnızsın."},
			{"Kimse sana", "inanmayacak."},
			{"Neden", "geri döndün?"},
			{"Ben hep", "buradaydım."},
	};

	private static final List<RegistryEntry<SoundEvent>> CREEPY = List.of(
			e(SoundEvents.AMBIENT_CAVE),
			e(SoundEvents.ENTITY_ENDERMAN_STARE),
			e(SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE),
			e(SoundEvents.ENTITY_WARDEN_HEARTBEAT),
			e(SoundEvents.BLOCK_WOODEN_DOOR_OPEN),
			e(SoundEvents.BLOCK_CHEST_OPEN),
			e(SoundEvents.ENTITY_ZOMBIE_AMBIENT)
	);

	// SoundEvents sabitleri sürüme göre SoundEvent ya da RegistryEntry olabilir; iki aşırı yükleme ikisini de karşılar.
	private static RegistryEntry<SoundEvent> e(SoundEvent s) {
		return RegistryEntry.of(s);
	}

	private static RegistryEntry<SoundEvent> e(RegistryEntry<SoundEvent> r) {
		return r;
	}

	static final class State {
		boolean active;
		boolean disabled;   // /ykens stop sonrası otomatik başlamasın
		int stage = 1;
		int sessionTicks;
		int stageTicks;

		BlockPos home;
		RegistryKey<World> homeWorld;
		boolean awayFromHome;

		int soundCooldown = 200;
		int chestCooldown = 800;
		int villagerCooldown = 1200;
		int weatherCooldown = 3000;
		int golemCooldown = 1800;
		int signCooldown = 2400;

		int stepsLeft;
		int stepTimer;
		double stepDist;

		BlockPos walkOrigin;
		RegistryKey<World> walkWorld;
		boolean trapDone;
	}

	// ---------------------------------------------------------------- API

	public static void start(ServerPlayerEntity p) {
		State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
		int session = s.sessionTicks;
		State fresh = new State();
		fresh.sessionTicks = session;
		fresh.active = true;
		fresh.home = p.getBlockPos();
		fresh.homeWorld = p.getServerWorld().getRegistryKey();
		STATES.put(p.getUuid(), fresh);
	}

	public static void stop(ServerPlayerEntity p) {
		State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
		s.active = false;
		s.disabled = true;
		s.walkOrigin = null;
		s.stepsLeft = 0;
	}

	public static void setStage(ServerPlayerEntity p, int stage) {
		State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
		if (!s.active) {
			start(p);
			s = STATES.get(p.getUuid());
		}
		s.stage = MathHelper.clamp(stage, 1, 3);
		s.stageTicks = 0;
	}

	public static void setHome(ServerPlayerEntity p) {
		State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
		s.home = p.getBlockPos();
		s.homeWorld = p.getServerWorld().getRegistryKey();
		s.awayFromHome = false;
	}

	public static String status(ServerPlayerEntity p) {
		State s = STATES.get(p.getUuid());
		if (s == null || !s.active) {
			return "Ykens uyumuyor ama uyanık da değil. (oturum: " + (s == null ? 0 : s.sessionTicks / 20) + " sn)";
		}
		return "Aşama " + s.stage
				+ " | yürüme hedefi: " + (s.walkOrigin != null ? "aktif" : "yok")
				+ " | tuzak: " + (s.trapDone ? "kuruldu" : "bekliyor");
	}

	public static void forget(UUID id) {
		STATES.remove(id);
	}

	public static boolean forceTrustSign(ServerPlayerEntity p) {
		State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
		if (!s.active) start(p);
		s = STATES.get(p.getUuid());
		return placeTrustSign(p, p.getServerWorld(), s);
	}

	public static void forceTrap(ServerPlayerEntity p) {
		lavaTrap(p, p.getServerWorld());
	}

	// ---------------------------------------------------------------- Ana döngü

	public static void tick(ServerPlayerEntity p) {
		State s = STATES.computeIfAbsent(p.getUuid(), k -> new State());
		s.sessionTicks++;
		if (!p.isAlive() || p.isSpectator()) return;
		ServerWorld world = p.getServerWorld();

		if (!s.active) {
			if (!s.disabled && s.sessionTicks >= AUTO_START_TICKS) {
				start(p);
				s = STATES.get(p.getUuid());
			} else {
				return;
			}
		}

		s.stageTicks++;
		if (s.stage < 3 && s.stageTicks >= STAGE_UP_TICKS) {
			s.stage++;
			s.stageTicks = 0;
		}

		// Yaklaşan ayak sesleri
		if (s.stepsLeft > 0 && --s.stepTimer <= 0) {
			playStep(p, world, s.stepDist);
			s.stepDist = Math.max(2.5, s.stepDist - 0.9);
			s.stepsLeft--;
			s.stepTimer = 8 + RNG.nextInt(4);
		}

		// Arkadan sesler
		if (--s.soundCooldown <= 0) {
			behindEvent(p, world, s);
			s.soundCooldown = soundDelay(s);
		}

		// Sandıkların düzeni bozulur
		if (SHUFFLE_CHESTS && --s.chestCooldown <= 0) {
			shuffleChest(world, p);
			s.chestCooldown = secs(40, 90);
		}

		if (s.stage >= 2) {
			if (REMOVE_VILLAGERS && --s.villagerCooldown <= 0) {
				vanishVillager(world, p);
				s.villagerCooldown = secs(45, 100);
			}
			if (CHANGE_WEATHER && --s.weatherCooldown <= 0) {
				if (world.getRegistryKey() == World.OVERWORLD && !world.isRaining()) {
					world.setWeather(0, 6000 + RNG.nextInt(6000), true, false);
				}
				s.weatherCooldown = secs(120, 300);
			}
		}

		if (s.stage >= 3) {
			if (GOLEMS_TURN_HOSTILE && --s.golemCooldown <= 0) {
				angerGolems(world, p);
				s.golemCooldown = secs(60, 120);
			}
			homeAndSigns(p, world, s);
			walkCheck(p, world, s);
		}
	}

	// ---------------------------------------------------------------- Olaylar

	private static int secs(int min, int max) {
		return 20 * (min + RNG.nextInt(max - min + 1));
	}

	private static int soundDelay(State s) {
		if (s.walkOrigin != null) return secs(3, 8);   // yürürken arkadan sesler hızlanır
		return switch (s.stage) {
			case 1 -> secs(40, 90);
			case 2 -> secs(20, 50);
			default -> secs(10, 30);
		};
	}

	private static Vec3d behind(ServerPlayerEntity p, double dist) {
		double r = Math.toRadians(p.getYaw());
		// Minecraft'ta ileri yön = (-sin(yaw), cos(yaw)); arka = tersi
		return new Vec3d(p.getX() + Math.sin(r) * dist, p.getY(), p.getZ() - Math.cos(r) * dist);
	}

	private static void playAt(ServerPlayerEntity p, Vec3d pos, RegistryEntry<SoundEvent> snd, float vol, float pitch) {
		p.networkHandler.sendPacket(new PlaySoundS2CPacket(
				snd, SoundCategory.HOSTILE, pos.x, pos.y, pos.z, vol, pitch, RNG.nextLong()));
	}

	private static void behindEvent(ServerPlayerEntity p, ServerWorld world, State s) {
		if (RNG.nextInt(100) < 35 && s.stepsLeft == 0) {
			s.stepsLeft = 5 + RNG.nextInt(6);
			s.stepDist = 9;
			s.stepTimer = 0;
		} else {
			RegistryEntry<SoundEvent> snd = CREEPY.get(RNG.nextInt(CREEPY.size()));
			playAt(p, behind(p, 4 + RNG.nextInt(5)), snd, 0.8f, 0.7f + RNG.nextFloat() * 0.5f);
		}
	}

	private static void playStep(ServerPlayerEntity p, ServerWorld world, double dist) {
		BlockState below = world.getBlockState(p.getBlockPos().down());
		SoundEvent step = below.getSoundGroup().getStepSound();
		playAt(p, behind(p, dist), RegistryEntry.of(step), 0.9f, 0.9f + RNG.nextFloat() * 0.2f);
	}

	private static void shuffleChest(ServerWorld world, ServerPlayerEntity p) {
		List<ChestBlockEntity> chests = new ArrayList<>();
		BlockPos c = p.getBlockPos();
		for (BlockPos pos : BlockPos.iterate(c.add(-10, -5, -10), c.add(10, 5, 10))) {
			if (world.getBlockEntity(pos) instanceof ChestBlockEntity chest && !chest.isEmpty()) {
				chests.add(chest);
			}
		}
		if (chests.isEmpty()) return;
		ChestBlockEntity chest = chests.get(RNG.nextInt(chests.size()));
		List<ItemStack> stacks = new ArrayList<>();
		for (int i = 0; i < chest.size(); i++) stacks.add(chest.getStack(i));
		Collections.shuffle(stacks, RNG);
		for (int i = 0; i < stacks.size(); i++) chest.setStack(i, stacks.get(i));
		chest.markDirty();
	}

	private static void vanishVillager(ServerWorld world, ServerPlayerEntity p) {
		Box box = p.getBoundingBox().expand(64);
		List<VillagerEntity> villagers = world.getEntitiesByClass(VillagerEntity.class, box, v -> true);
		if (villagers.isEmpty()) return;
		VillagerEntity v = villagers.get(RNG.nextInt(villagers.size()));
		world.spawnParticles(ParticleTypes.SMOKE, v.getX(), v.getY() + 1.0, v.getZ(), 10, 0.2, 0.4, 0.2, 0.01);
		v.discard();
	}

	private static void angerGolems(ServerWorld world, ServerPlayerEntity p) {
		for (IronGolemEntity g : world.getEntitiesByClass(IronGolemEntity.class, p.getBoundingBox().expand(24), g -> true)) {
			g.setAngryAt(p.getUuid());
			g.chooseRandomAngerTime();
			g.setTarget(p);
		}
	}

	// ---------------------------------------------------------------- Ev, tabela, yürüyüş

	private static void homeAndSigns(ServerPlayerEntity p, ServerWorld world, State s) {
		if (s.home == null || s.homeWorld == null || !s.homeWorld.equals(world.getRegistryKey())) return;

		double dx = p.getX() - s.home.getX();
		double dz = p.getZ() - s.home.getZ();
		double d2 = dx * dx + dz * dz;
		if (d2 > (double) AWAY_RADIUS * AWAY_RADIUS) s.awayFromHome = true;

		if (s.signCooldown > 0) s.signCooldown--;
		if (s.signCooldown > 0) return;

		// Eve geri dönünce "bana güven" tabelası
		if (LAVA_TRAP && s.awayFromHome && d2 < (double) HOME_RADIUS * HOME_RADIUS
				&& s.walkOrigin == null && !s.trapDone) {
			if (placeTrustSign(p, world, s)) {
				s.awayFromHome = false;
				s.signCooldown = secs(120, 240);
			} else {
				s.signCooldown = 100;
			}
			return;
		}

		// Aksi halde ara sıra fısıltı tabelası (bazen ters yazılı)
		if (RNG.nextInt(100) < 60) {
			String[] lines = SIGN_WHISPERS[RNG.nextInt(SIGN_WHISPERS.length)];
			placeSignNear(p, world, lines, RNG.nextInt(100) < 40);
		}
		s.signCooldown = secs(180, 360);
	}

	private static boolean placeTrustSign(ServerPlayerEntity p, ServerWorld world, State s) {
		BlockPos pos = placeSignNear(p, world, SIGN_TRUST, false);
		if (pos == null) return false;
		s.walkOrigin = pos;
		s.walkWorld = world.getRegistryKey();
		playAt(p, behind(p, 3), e(SoundEvents.ENTITY_WARDEN_HEARTBEAT), 1.0f, 0.8f);
		return true;
	}

	private static void walkCheck(ServerPlayerEntity p, ServerWorld world, State s) {
		if (s.walkOrigin == null) return;
		if (!world.getRegistryKey().equals(s.walkWorld)) {
			s.walkOrigin = null;   // boyut değiştirdi, hedef iptal
			return;
		}
		if (s.sessionTicks % 20 != 0) return;
		double dx = p.getX() - s.walkOrigin.getX();
		double dz = p.getZ() - s.walkOrigin.getZ();
		if (dx * dx + dz * dz >= (double) WALK_DISTANCE * WALK_DISTANCE) {
			lavaTrap(p, world);
			s.walkOrigin = null;
			s.trapDone = true;
		}
	}

	// ---------------------------------------------------------------- Tabela yerleştirme

	private static BlockPos findSignSpot(ServerWorld world, ServerPlayerEntity p) {
		double r = Math.toRadians(p.getYaw());
		double fx = -Math.sin(r);
		double fz = Math.cos(r);
		BlockPos base = p.getBlockPos();
		for (int dist = 2; dist <= 5; dist++) {
			BlockPos col = base.add((int) Math.round(fx * dist), 0, (int) Math.round(fz * dist));
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos pos = col.up(dy);
				BlockPos below = pos.down();
				if (world.getBlockState(pos).isAir()
						&& world.getBlockState(below).isSideSolidFullSquare(world, below, Direction.UP)) {
					return pos;
				}
			}
		}
		return null;
	}

	private static String reverse(String s) {
		return new StringBuilder(s).reverse().toString();
	}

	private static BlockPos placeSignNear(ServerPlayerEntity p, ServerWorld world, String[] lines, boolean reversed) {
		BlockPos spot = findSignSpot(world, p);
		if (spot == null) return null;
		String[] out = new String[4];
		for (int i = 0; i < 4; i++) {
			String line = i < lines.length ? lines[i] : "";
			out[i] = reversed ? reverse(line) : line;
		}
		int rot = RotationPropertyHelper.fromYaw(p.getYaw() + 180.0F);
		return placeSign(world, spot, rot, out) ? spot : null;
	}

	private static boolean placeSign(ServerWorld world, BlockPos pos, int rotation, String[] lines) {
		BlockState state = Blocks.OAK_SIGN.getDefaultState().with(SignBlock.ROTATION, rotation);
		world.setBlockState(pos, state, Block.NOTIFY_ALL);
		BlockEntity be = world.getBlockEntity(pos);
		if (!(be instanceof SignBlockEntity sign)) return false;

		SignText text = new SignText();
		for (int i = 0; i < 4 && i < lines.length; i++) {
			text = text.withMessage(i, Text.literal(lines[i]));
		}
		text = text.withColor(DyeColor.RED);
		sign.setText(text, true);
		sign.setWaxed(true);   // oyuncu düzenleyemesin
		sign.markDirty();
		world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
		return true;
	}

	// ---------------------------------------------------------------- Lav tuzağı

	private static void lavaTrap(ServerPlayerEntity p, ServerWorld world) {
		double r = Math.toRadians(p.getYaw());
		int cx = MathHelper.floor(p.getX() - Math.sin(r) * 8);
		int cz = MathHelper.floor(p.getZ() + Math.cos(r) * 8);

		int top = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, cx, cz);
		int gy = top - 1;   // yüzey bloğunun y'si
		BlockState lava = Blocks.LAVA.getDefaultState();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				world.setBlockState(new BlockPos(cx + dx, gy, cz + dz), lava, Block.NOTIFY_ALL);
				world.setBlockState(new BlockPos(cx + dx, gy - 1, cz + dz), lava, Block.NOTIFY_ALL);
			}
		}

		// Tabela: oyuncuya bakan kenarda
		int sx = cx + (int) Math.round(Math.sin(r) * 3);
		int sz = cz - (int) Math.round(Math.cos(r) * 3);
		int sTop = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, sx, sz);
		BlockPos signPos = new BlockPos(sx, sTop, sz);
		if (world.getBlockState(signPos).isAir()) {
			placeSign(world, signPos, RotationPropertyHelper.fromYaw(p.getYaw() + 180.0F), SIGN_LAVA);
		}

		playAt(p, behind(p, 3), e(SoundEvents.ENTITY_WARDEN_NEARBY_CLOSE), 1.0f, 0.6f);
	}
}
