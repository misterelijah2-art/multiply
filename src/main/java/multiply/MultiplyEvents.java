package multiply;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MultiplyEvents {

    private static final int RADIUS = 5;
    private static final Random RANDOM = new Random();

    // Minimum ticks standing on ground before a jump is considered valid
    private static final int MIN_GROUND_TICKS = 3;
    // Vanilla jump Y velocity is ~0.42; knockback is typically much lower (~0.1-0.25)
    private static final double JUMP_Y_THRESHOLD = 0.38;

    private static final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private static final Map<UUID, Integer> groundTicks = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    UUID uuid = player.getUUID();

                    boolean onGround = player.onGround();
                    boolean wasGround = wasOnGround.getOrDefault(uuid, true);

                    if (onGround) {
                        groundTicks.merge(uuid, 1, Integer::sum);
                    } else {
                        // Just left the ground
                        if (wasGround) {
                            double yVel = player.getDeltaMovement().y;
                            int ticksOnGround = groundTicks.getOrDefault(uuid, 0);

                            // Only trigger if: upward velocity matches a real jump
                            // AND player was standing still enough (not knocked mid-air)
                            if (yVel >= JUMP_Y_THRESHOLD && ticksOnGround >= MIN_GROUND_TICKS) {
                                triggerMultiply(level, player);
                            }
                        }
                        groundTicks.put(uuid, 0);
                    }

                    wasOnGround.put(uuid, onGround);
                }
            }
        });
    }

    private static void triggerMultiply(ServerLevel level, ServerPlayer player) {
        BlockPos center = player.blockPosition();
        AABB searchBox = new AABB(
            center.getX() - RADIUS, center.getY() - RADIUS, center.getZ() - RADIUS,
            center.getX() + RADIUS, center.getY() + RADIUS, center.getZ() + RADIUS
        );

        // Duplicate nearby living entities (excluding all players)
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            e -> !(e instanceof Player)
        );

        for (LivingEntity entity : nearbyEntities) {
            EntityType<?> type = entity.getType();
            LivingEntity copy = (LivingEntity) type.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (copy != null) {
                copy.teleportTo(entity.getX(), entity.getY(), entity.getZ());
                copy.setYRot(entity.getYRot());
                copy.setXRot(entity.getXRot());
                level.addFreshEntity(copy);
            }
        }

        // Duplicate nearby dropped item entities with a small random launch
        List<ItemEntity> nearbyItems = level.getEntitiesOfClass(
            ItemEntity.class,
            searchBox,
            e -> true
        );

        for (ItemEntity item : nearbyItems) {
            ItemEntity copy = new ItemEntity(
                level,
                item.getX(), item.getY(), item.getZ(),
                item.getItem().copy()
            );
            double dx = (RANDOM.nextDouble() - 0.5) * 0.3;
            double dy = 0.15 + RANDOM.nextDouble() * 0.15;
            double dz = (RANDOM.nextDouble() - 0.5) * 0.3;
            copy.setDeltaMovement(new Vec3(dx, dy, dz));
            level.addFreshEntity(copy);
        }
    }
}
