package multiply;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Multiply implements ModInitializer {
    public static final String MOD_ID = "multiply";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        MultiplyEvents.register();
        LOGGER.info("Multiply initialized.");
    }
}
