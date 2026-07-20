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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MultiplyEvents {

    private static final int RADIUS = 5;
    // Cooldown in ticks (40 = 2 seconds)
    private static final int COOLDOWN_TICKS = 40;

    private static final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private static final Map<UUID, Integer> cooldowns = new HashMap<>();
    // Tracks how many ticks the player has been airborne (to distinguish jump vs fall)
    private static final Map<UUID, Integer> airTicks = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    UUID uuid = player.getUUID();

                    cooldowns.put(uuid, Math.max(0, cooldowns.getOrDefault(uuid, 0) - 1));

                    boolean onGround = player.onGround();
                    boolean wasGround = wasOnGround.getOrDefault(uuid, true);

                    if (onGround) {
                        // Reset air counter when on ground
                        airTicks.put(uuid, 0);
                    } else {
                        airTicks.put(uuid, airTicks.getOrDefault(uuid, 0) + 1);
                    }

                    // Jump = just left the ground AND vertical velocity is upward (positive Y delta)
                    // This prevents a fall-landing from triggering (fall = was in air a long time, then lands)
                    boolean justLeftGround = wasGround && !onGround;
                    boolean movingUpward = player.getDeltaMovement().y > 0.1;

                    if (justLeftGround && movingUpward && cooldowns.getOrDefault(uuid, 0) == 0) {
                        triggerMultiply(level, player);
                        cooldowns.put(uuid, COOLDOWN_TICKS);
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

        // Duplicate nearby living entities (mobs, animals — not the player)
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

        // Duplicate nearby dropped item entities
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
            copy.setDeltaMovement(item.getDeltaMovement());
            level.addFreshEntity(copy);
        }
    }
}
