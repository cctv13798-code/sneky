package com.example.ykens;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YkensMod implements ModInitializer {
	public static final String MOD_ID = "ykens";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
				HauntManager.tick(player);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				HauntManager.forget(handler.player.getUuid()));

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(CommandManager.literal("ykens")
						.requires(src -> src.hasPermissionLevel(2))
						.then(CommandManager.literal("start").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							HauntManager.start(p);
							reply(ctx.getSource(), "Ykens uyandı. (aşama 1)");
							return 1;
						}))
						.then(CommandManager.literal("stop").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							HauntManager.stop(p);
							reply(ctx.getSource(), "Ykens sustu.");
							return 1;
						}))
						.then(CommandManager.literal("stage")
								.then(CommandManager.argument("n", IntegerArgumentType.integer(1, 3))
										.executes(ctx -> {
											ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
											int n = IntegerArgumentType.getInteger(ctx, "n");
											HauntManager.setStage(p, n);
											reply(ctx.getSource(), "Aşama: " + n);
											return 1;
										})))
						.then(CommandManager.literal("sethome").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							HauntManager.setHome(p);
							reply(ctx.getSource(), "Ev konumu buraya ayarlandı.");
							return 1;
						}))
						.then(CommandManager.literal("sign").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							boolean ok = HauntManager.forceTrustSign(p);
							reply(ctx.getSource(), ok ? "Tabela bırakıldı. 1000 blok say." : "Tabela için uygun yer bulunamadı.");
							return ok ? 1 : 0;
						}))
						.then(CommandManager.literal("trap").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							HauntManager.forceTrap(p);
							reply(ctx.getSource(), "Lav tuzağı kuruldu (önünde ~8 blok).");
							return 1;
						}))
						.then(CommandManager.literal("status").executes(ctx -> {
							ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
							reply(ctx.getSource(), HauntManager.status(p));
							return 1;
						}))));

		LOGGER.info("Ykens seni izliyor.");
	}

	private static void reply(ServerCommandSource src, String msg) {
		src.sendFeedback(() -> Text.literal(msg), false);
	}
}
