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
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    int killed = 0;

                    for (ServerLevel level : ctx.getSource().getServer().getAllLevels()) {
                        // Collect all non-player living entities
                        List<Entity> toKill = new ArrayList<>();

                        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, 
                                level.getWorldBorder().createAabb(), 
                                e -> !(e instanceof Player))) {
                            toKill.add(e);
                        }

                        // Collect all dropped item entities
                        toKill.addAll(level.getEntitiesOfClass(ItemEntity.class,
                                level.getWorldBorder().createAabb(),
                                e -> true));

                        for (Entity e : toKill) {
                            e.discard();
                            killed++;
                        }
                    }

                    ctx.getSource().sendSuccess(
                        () -> Component.literal("[Multiply] Cleaned " + killed + " entities."),
                        true
                    );
                    return killed;
                })
        );
    }
}
