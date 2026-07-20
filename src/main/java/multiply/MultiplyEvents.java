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

    // 1 tick on ground is enough — the XZ speed gate handles knockback filtering.
    // Sprint-jumps only touch the ground for 1-2 ticks between hops, so 3 was
    // silently blocking every jump after the first.
    private static final int MIN_GROUND_TICKS = 1;

    // Max XZ speed that still counts as a voluntary jump.
    // Walk ~0.20, sprint ~0.28, sprint-jump ~0.30.
    // Knockback launches are typically 0.40+ horizontally.
    private static final double MAX_JUMP_XZ_SPEED = 0.36;

    private static final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private static final Map<UUID, Integer> groundTicks = new HashMap<>();
    private static final Map<UUID, Double> xzSpeedOnGround = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    UUID uuid = player.getUUID();

                    boolean onGround = player.onGround();
                    boolean wasGround = wasOnGround.getOrDefault(uuid, true);
                    Vec3 vel = player.getDeltaMovement();
                    double xzSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);

                    if (onGround) {
                        groundTicks.merge(uuid, 1, Integer::sum);
                        xzSpeedOnGround.put(uuid, xzSpeed);
                    } else {
                        if (wasGround) {
                            int ticksOnGround = groundTicks.getOrDefault(uuid, 0);
                            double launchXZ = xzSpeedOnGround.getOrDefault(uuid, 0.0);

                            if (ticksOnGround >= MIN_GROUND_TICKS && launchXZ <= MAX_JUMP_XZ_SPEED) {
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
