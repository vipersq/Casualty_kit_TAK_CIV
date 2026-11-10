package io.github.vipersq.casualtykit.atak;

import android.content.Context;
import android.widget.FrameLayout;

import com.atak.plugins.impl.PluginContextProvider;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

/** Plugin entry point: a toolbar button that opens the casualty pane. */
public class CasualtyKitPlugin implements IPlugin {

    private final Context pluginContext;
    private final IHostUIService uiService;
    private final ToolbarItem toolbarItem;
    private final Repo repo = new Repo();
    private Pane pane;
    private Ui ui;

    public CasualtyKitPlugin(IServiceController serviceController) {
        PluginContextProvider ctxProvider = serviceController.getService(PluginContextProvider.class);
        pluginContext = ctxProvider.getPluginContext();
        pluginContext.setTheme(R.style.ATAKPluginTheme);
        uiService = serviceController.getService(IHostUIService.class);

        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                })
                .build();
    }

    @Override
    public void onStart() {
        repo.start();
        if (uiService != null)
            uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        repo.stop();
        if (uiService != null)
            uiService.removeToolbarItem(toolbarItem);
    }

    private void showPane() {
        if (pane == null) {
            FrameLayout root = new FrameLayout(pluginContext);
            ui = new Ui(pluginContext, root, repo);
            pane = new PaneBuilder(root)
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }
        ui.showList();
        if (!uiService.isPaneVisible(pane))
            uiService.showPane(pane, null);
    }
}
