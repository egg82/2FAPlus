package me.egg82.tfaplus.hooks;

import com.djrapitops.plan.api.PlanAPI;
import com.djrapitops.plan.data.element.AnalysisContainer;
import com.djrapitops.plan.data.element.InspectContainer;
import com.djrapitops.plan.data.plugin.ContainerSize;
import com.djrapitops.plan.data.plugin.PluginData;
import java.util.Collection;
import java.util.UUID;
import me.egg82.tfaplus.TFAAPI;

public class PlayerAnalyticsHook implements PluginHook {
    public PlayerAnalyticsHook() { PlanAPI.getInstance().addPluginDataSource(new Data()); }

    public void cancel() {}

    class Data extends PluginData {
        private final TFAAPI api = TFAAPI.getInstance();

        private Data() {
            super(ContainerSize.THIRD, "2FAPlus");
            setPluginIcon("mobile-alt");
            setIconColor("green");
        }

        public InspectContainer getPlayerData(UUID uuid, InspectContainer container) {
            container.addValue("Is Registered", (api.isRegistered(uuid)) ? "Yes" : "No");
            return container;
        }

        public AnalysisContainer getServerData(Collection<UUID> uuids, AnalysisContainer container) {
            int registrations = 0;
            for (UUID uuid : uuids) {
                if (api.isRegistered(uuid)) {
                    registrations++;
                }
            }

            container.addValue("Registered Players", registrations);

            return container;
        }
    }
}
