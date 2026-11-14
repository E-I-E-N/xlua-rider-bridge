package local.xlua;

import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.FakePsiElement;
import javax.swing.Icon;

final class XluaNavigationTarget extends FakePsiElement {
    private final Project project;
    private final String qualifiedName;

    XluaNavigationTarget(PsiElement source, String qualifiedName) {
        this.project = source.getProject();
        this.qualifiedName = qualifiedName;
    }

    @Override public PsiElement getParent() { return null; }
    @Override public Project getProject() { return project; }
    @Override public String getName() { return qualifiedName; }
    @Override public boolean isValid() { return !project.isDisposed(); }
    @Override public boolean canNavigate() { return isValid(); }
    @Override public boolean canNavigateToSource() { return canNavigate(); }
    @Override public String getText() { return qualifiedName; }
    @Override public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override public String getPresentableText() { return qualifiedName; }
            @Override public String getLocationString() { return "CSharp / XLua"; }
            @Override public Icon getIcon(boolean unused) { return null; }
        };
    }

    @Override
    public void navigate(boolean requestFocus) {
        if (!project.isDisposed()) project.getService(XluaNavigationService.class).navigate(qualifiedName, requestFocus);
    }
}
