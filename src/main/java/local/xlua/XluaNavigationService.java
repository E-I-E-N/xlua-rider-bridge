package local.xlua;

import com.intellij.navigation.NavigationItem;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.jetbrains.rdclient.gotoType.ProtocolNavigationItem;
import com.jetbrains.rider.globalNavigation.ProtocolGotoClassContributor;
import com.jetbrains.rider.globalNavigation.ProtocolGotoContributorBase;
import com.jetbrains.rider.globalNavigation.ProtocolGotoSymbolContributor;
import com.jetbrains.rider.globalNavigation.RdDotnetGotoService;
import com.jetbrains.rider.globalNavigation.RdDotnetGotoSession;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class XluaNavigationService implements Disposable {
    private final Project project;
    private final Deque<Disposable> sessions = new ArrayDeque<>();

    public XluaNavigationService(Project project) { this.project = project; }
    @Override public void dispose() {}

    public void navigate(String name, boolean requestFocus) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "XLua: " + name, true) {
            @Override
            public void run(ProgressIndicator indicator) {
                Disposable session = Disposer.newDisposable("XLua navigation " + name);
                Disposer.register(XluaNavigationService.this, session);
                synchronized (sessions) {
                    sessions.addLast(session);
                    while (sessions.size() > 8) Disposer.dispose(sessions.removeFirst());
                }
                try {
                    List<NavigationItem> targets = find(name, new ProtocolGotoClassContributor(), session);
                    if (targets.isEmpty()) targets = find(name, new ProtocolGotoSymbolContributor(), session);
                    List<NavigationItem> result = targets;
                    ApplicationManager.getApplication().invokeLater(() -> {
                        if (project.isDisposed()) { Disposer.dispose(session); return; }
                        if (result.isEmpty()) {
                            notifyMissing(name);
                            Disposer.dispose(session);
                        } else if (result.size() == 1) {
                            result.getFirst().navigate(requestFocus);
                        } else {
                            var popup = JBPopupFactory.getInstance().createPopupChooserBuilder(result)
                                .setTitle("CSharp: " + name)
                                .setNamerForFiltering(NavigationItem::getName)
                                .setItemChosenCallback(item -> item.navigate(requestFocus))
                                .createPopup();
                            popup.showCenteredInCurrentWindow(project);
                        }
                    }, project.getDisposed());
                } catch (RuntimeException e) {
                    Disposer.dispose(session);
                    throw e;
                }
            }
        });
    }

    private List<NavigationItem> find(String name, ProtocolGotoContributorBase contributor, Disposable session) {
        List<NavigationItem> targets = new ArrayList<>();
        String shortName = name.substring(name.lastIndexOf('.') + 1);
        RdDotnetGotoSession lookup = RdDotnetGotoService.getInstance(project).getOrBindGotoSession(session, contributor.getKey());
        List<String> names = lookup.requestNamesBlockingAndCacheGotoResults(name, true);
        if (names.isEmpty()) names = lookup.requestNamesBlockingAndCacheGotoResults(shortName, true);
        for (String key : names) ReadAction.run(() -> lookup.processBoundItemsWithNavItemsCacheLock(project, key, item -> {
            if (item instanceof ProtocolNavigationItem protocol) {
                String clrName = protocol.getSearchItemData().getClrName();
                if (matches(name, clrName == null ? item.getName() : clrName)) targets.add(item);
            }
            return targets.size() < 64;
        }));
        return targets;
    }

    static boolean matches(String expected, String clrName) {
        if (clrName == null) return false;
        String normalized = clrName.replace('+', '.');
        int parameterStart = normalized.indexOf('(');
        if (parameterStart >= 0) normalized = normalized.substring(0, parameterStart);
        if (normalized.length() > 2 && normalized.charAt(1) == ':') normalized = normalized.substring(2);
        return normalized.equals(expected);
    }

    private void notifyMissing(String name) {
        NotificationGroupManager.getInstance().getNotificationGroup("XLua Navigation")
            .createNotification("Cannot resolve CSharp symbol: " + name, NotificationType.INFORMATION).notify(project);
    }
}
