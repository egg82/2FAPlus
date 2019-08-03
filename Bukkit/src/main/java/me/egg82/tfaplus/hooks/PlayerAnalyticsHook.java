package me.egg82.tfaplus.hooks;

import com.djrapitops.plan.capability.CapabilityService;
import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.Caller;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.ExtensionService;
import com.djrapitops.plan.extension.annotation.BooleanProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import java.util.Optional;
import java.util.UUID;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.TFAAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerAnalyticsHook implements PluginHook {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private boolean enabled = false;
    private Caller caller;

    public PlayerAnalyticsHook() {
        if (!registerExtension()) {
            return;
        }
        enabled = true;

        CapabilityService.getInstance().registerEnableListener(enabled -> {
            if (enabled) {
                this.enabled = registerExtension();
            } else {
                this.enabled = false;
            }
        });
    }

    public void cancel() {}

    public void update(UUID uuid, String name) {
        if (enabled) {
            caller.updatePlayerData(uuid, name);
        }
    }

    private boolean registerExtension() {
        try {
            if (!CapabilityService.getInstance().hasCapability("DATA_EXTENSION_VALUES")) {
                logger.error("Your version of Plan is outdated.");
                return false;
            }

            DataExtension extension = new Data();
            Optional<Caller> caller = ExtensionService.getInstance().register(extension);
            if (!caller.isPresent()) {
                logger.error("Could not register data extension for Plan.");
                return false;
            }
            this.caller = caller.get();
            return true;
        } catch (NoClassDefFoundError ex) {
            logger.error("Your version of Plan is outdated.", ex);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            logger.error("Could not register data extension for Plan.", ex);
        }
        return false;
    }

    @PluginInfo(
            name = "2FA+",
            iconName = "mobile-alt",
            iconFamily = Family.SOLID,
            color = Color.GREEN
    )
    public class Data implements DataExtension {
        private final TFAAPI api = TFAAPI.getInstance();

        private Data() { }

        @BooleanProvider(
                text = "Is Registered",
                description = "Whether or not the player is registered with any kind of 2FA.",
                iconName = "fingerprint",
                iconFamily = Family.SOLID,
                iconColor = Color.GREY
        )
        public boolean isPlayerRegistered(UUID uuid) throws APIException { return api.isRegistered(uuid); }

        public CallEvents[] callExtensionMethodsOn() { return new CallEvents[] { CallEvents.PLAYER_JOIN, CallEvents.PLAYER_LEAVE, CallEvents.MANUAL }; }
    }
}
