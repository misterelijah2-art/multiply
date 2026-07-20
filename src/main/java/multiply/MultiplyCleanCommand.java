package multiply;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public class MultiplyCleanCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            registerCommand(dispatcher)
        );
    }

    private static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("multiplyclean")
                .requires(src -> src.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> {
                    int killed = 0;

                    for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                        WorldBorder border = level.getWorldBorder();
                        AABB searchBox = new AABB(
                            border.getMinX(), level.getMinBuildHeight(),  border.getMinZ(),
                            border.getMaxX(), level.getMaxBuildHeight(), border.getMaxZ()
                        );

                        List<Entity> toKill = new ArrayList<>();

                        toKill.addAll(level.getEntitiesOfClass(LivingEntity.class, searchBox,
                            e -> !(e instanceof Player)));

                        toKill.addAll(level.getEntitiesOfClass(ItemEntity.class, searchBox,
                            e -> true));

                        for (Entity e : toKill) {
                            e.discard();
                            killed++;
                        }
                    }

                    final int total = killed;
                    ctx.getSource().sendSuccess(
                        () -> Component.literal("[Multiply] Cleaned " + total + " entities."),
                        true
                    );
                    return total;
                })
        );
    }
}
