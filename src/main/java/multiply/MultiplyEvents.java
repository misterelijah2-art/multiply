package multiply;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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

    private static final Map<UUID, Boolean> wasOnGround = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    UUID uuid = player.getUUID();

                    boolean onGround = player.onGround();
                    boolean wasGround = wasOnGround.getOrDefault(uuid, true);

                    boolean justLeftGround = wasGround && !onGround;
                    boolean movingUpward = player.getDeltaMovement().y > 0.1;

                    if (justLeftGround && movingUpward) {
                        triggerMultiply(level, player);
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

        // Duplicate nearby living entities
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            e -> e != player
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

            // Small random burst: slight horizontal scatter + tiny upward pop
            double dx = (RANDOM.nextDouble() - 0.5) * 0.3;
            double dy = 0.15 + RANDOM.nextDouble() * 0.15;
            double dz = (RANDOM.nextDouble() - 0.5) * 0.3;
            copy.setDeltaMovement(new Vec3(dx, dy, dz));

            level.addFreshEntity(copy);
        }
    }
}
