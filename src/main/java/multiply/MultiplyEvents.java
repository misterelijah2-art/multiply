package multiply;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MultiplyEvents {

    private static final int RADIUS = 5;
    private static final int COOLDOWN_TICKS = 40;

    private static final Map<UUID, Boolean> wasOnGround = new HashMap<>();
    private static final Map<UUID, Integer> cooldowns = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (ServerPlayer player : level.players()) {
                    UUID uuid = player.getUUID();

                    // Tick down cooldown
                    cooldowns.put(uuid, Math.max(0, cooldowns.getOrDefault(uuid, 0) - 1));

                    boolean onGround = player.onGround();
                    boolean wasGround = wasOnGround.getOrDefault(uuid, true);

                    // Jump detected: was on ground last tick, now airborne
                    if (wasGround && !onGround && cooldowns.getOrDefault(uuid, 0) == 0) {
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

        // Duplicate nearby living entities (excluding the player)
        List<LivingEntity> nearbyEntities = level.getEntitiesOfClass(
            LivingEntity.class,
            searchBox,
            e -> e != player
        );

        for (LivingEntity entity : nearbyEntities) {
            EntityType<?> type = entity.getType();
            LivingEntity copy = (LivingEntity) type.create(level, MobSpawnType.MOB_SUMMONED);
            if (copy != null) {
                copy.moveTo(
                    entity.getX(), entity.getY(), entity.getZ(),
                    entity.getYRot(), entity.getXRot()
                );
                level.addFreshEntity(copy);
            }
        }

        // Duplicate floor blocks (place a copy one layer above each floor block)
        for (int x = -RADIUS; x <= RADIUS; x++) {
            for (int z = -RADIUS; z <= RADIUS; z++) {
                BlockPos floorPos = new BlockPos(center.getX() + x, center.getY() - 1, center.getZ() + z);
                BlockState state = level.getBlockState(floorPos);

                if (state.isAir() || state.is(Blocks.BEDROCK)) continue;

                BlockPos above = floorPos.above();
                if (!level.getBlockState(above).isAir()) continue;

                level.setBlock(above, state, 3);
            }
        }
    }
}
