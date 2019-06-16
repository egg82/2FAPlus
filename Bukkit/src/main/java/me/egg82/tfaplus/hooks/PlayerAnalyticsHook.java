package me.egg82.tfaplus.hooks;

import com.djrapitops.plan.api.PlanAPI;
import com.djrapitops.plan.data.element.AnalysisContainer;
import com.djrapitops.plan.data.element.InspectContainer;
import com.djrapitops.plan.data.plugin.ContainerSize;
import com.djrapitops.plan.data.plugin.PluginData;
import com.djrapitops.plan.utilities.html.icon.Color;
import com.djrapitops.plan.utilities.html.icon.Icon;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import me.egg82.tfaplus.APIException;
import me.egg82.tfaplus.TFAAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlayerAnalyticsHook implements PluginHook {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public PlayerAnalyticsHook() { PlanAPI.getInstance().addPluginDataSource(new Data()); }

    public void cancel() {}

    class Data extends PluginData {
        private final TFAAPI api = TFAAPI.getInstance();

        private Data() {
            super(ContainerSize.THIRD, "2FAPlus");
            setPluginIcon(Icon.called("mobile-alt").of(Color.GREEN).build());
        }

        public InspectContainer getPlayerData(UUID uuid, InspectContainer container) {
            Optional<Boolean> registered = Optional.empty();
            try {
                registered = Optional.of(api.isRegistered(uuid));
            } catch (APIException ex) {
                logger.error(ex.getMessage(), ex);
            }

            container.addValue("Is Registered", registered.isPresent() ? (registered.get() ? "Yes" : "No") : "ERROR");
            return container;
        }

        public AnalysisContainer getServerData(Collection<UUID> uuids, AnalysisContainer container) {
            int registrations = 0;
            for (UUID uuid : uuids) {
                try {
                    if (api.isRegistered(uuid)) {
                        registrations++;
                    }
                } catch (APIException ex) {
                    logger.error(ex.getMessage(), ex);
                }
            }

            container.addValue("Registered Players", registrations);

            return container;
        }
    }
}
