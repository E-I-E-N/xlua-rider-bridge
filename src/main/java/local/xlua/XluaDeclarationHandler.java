package local.xlua;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.lua.psi.impl.LuaIndexExprImpl;
import com.intellij.lua.psi.impl.LuaNameExprImpl;
import com.intellij.lua.resolve.LuaDeclarations;
import com.intellij.lua.psi.LuaElementTypes;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.HashSet;
import java.util.Arrays;
import java.util.Set;

public final class XluaDeclarationHandler implements GotoDeclarationHandler {
    @Override
    public PsiElement[] getGotoDeclarationTargets(PsiElement source, int offset, Editor editor) {
        if (source == null || !source.getLanguage().getID().equals("Lua")) return null;
        LuaIndexExprImpl member = PsiTreeUtil.getParentOfType(source, LuaIndexExprImpl.class, false);
        if (member == null || member.getNameIdentifier() == null ||
            !member.getNameIdentifier().getTextRange().containsOffset(offset)) return null;
        String path = csharpPath(member, new HashSet<>(), 0);
        if (path == null || !path.startsWith("CS.") || path.length() <= 3) return null;
        return new PsiElement[]{new XluaNavigationTarget(source, path.substring(3))};
    }

    static String csharpPath(PsiElement expression, Set<PsiElement> seen, int depth) {
        if (expression == null || depth > 16 || !seen.add(expression)) return null;
        if (expression instanceof LuaIndexExprImpl index) {
            String name = index.getName();
            if (name == null || !name.matches("[A-Za-z_][A-Za-z_0-9]*")) return null;
            for (PsiElement child : index.getChildren()) {
                if (!(child instanceof LuaNameExprImpl) && !(child instanceof LuaIndexExprImpl)) continue;
                String parent = csharpPath(child, seen, depth + 1);
                return parent == null ? null : parent + "." + name;
            }
            return null;
        }
        if (!(expression instanceof LuaNameExprImpl name)) return null;
        PsiReference reference = name.getReference();
        PsiElement declaration = reference == null ? null : reference.resolve();
        if ("CS".equals(name.getReferenceName()) && (declaration == null || declaration == expression)) return "CS";
        if (declaration == null) return null;
        PsiElement initializer = LuaDeclarations.INSTANCE.initializerExpr(declaration);
        PsiElement variables = declaration.getParent();
        if (initializer == null && variables != null && variables.getNode().getElementType() == LuaElementTypes.VAR_LIST) {
            PsiElement assignment = variables.getParent();
            if (assignment != null && assignment.getNode().getElementType() == LuaElementTypes.ASSIGN_STAT) {
                var values = assignment.getNode().findChildByType(LuaElementTypes.EXPR_LIST);
                int index = Arrays.asList(variables.getChildren()).indexOf(declaration);
                if (values != null && index >= 0) {
                    PsiElement[] expressions = values.getPsi().getChildren();
                    if (index < expressions.length) initializer = expressions[index];
                }
            }
        }
        return csharpPath(initializer, seen, depth + 1);
    }
}
