package pl.mjedynak.idea.plugins.builder.gui;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.RecentsManager;
import com.intellij.ui.ReferenceEditorComboWithBrowseButton;
import com.intellij.util.IncorrectOperationException;
import pl.mjedynak.idea.plugins.builder.factory.ReferenceEditorComboWithBrowseButtonFactory;
import pl.mjedynak.idea.plugins.builder.factory.impl.PackageChooserDialogFactoryImpl;
import pl.mjedynak.idea.plugins.builder.helper.PsiHelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

public class CreateBuilderDialog extends DialogWrapper {

    static final String RECENTS_KEY = "CreateBuilderDialog.RecentsKey";

    private PsiHelper psiHelper;
    private Project project;
    private Module module;
    private PsiDirectory targetDirectory;
    private JTextField targetClassNameField;
    private ReferenceEditorComboWithBrowseButton targetPackageField;

    public CreateBuilderDialog(Project project,
                               String title,
                               String targetClassName,
                               PsiPackage targetPackage,
                               Module targetModule,
                               PsiHelper psiHelper,
                               PsiManager psiManager,
                               ReferenceEditorComboWithBrowseButtonFactory referenceEditorComboWithBrowseButtonFactory) {
        super(project, true);
        this.psiHelper = psiHelper;
        this.project = project;
        module = targetModule;
        targetClassNameField = new JTextField(targetClassName);
        setPreferredSize(targetClassNameField);

        String targetPackageName = (targetPackage != null) ? targetPackage.getQualifiedName() : "";
        targetPackageField = referenceEditorComboWithBrowseButtonFactory.getReferenceEditorComboWithBrowseButton(psiManager, targetPackageName, RECENTS_KEY);
        targetPackageField.addActionListener(new ChooserDisplayerActionListener(targetPackageField, new PackageChooserDialogFactoryImpl(), project));
        setTitle(title);
    }

    @Override
    public void show() {
        super.init();
        super.show();
    }

    private void setPreferredSize(JTextField field) {
        Dimension size = field.getPreferredSize();
        FontMetrics fontMetrics = field.getFontMetrics(field.getFont());
        size.width = fontMetrics.charWidth('a') * 40;
        field.setPreferredSize(size);
    }

    protected Action[] createActions() {
        return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
    }

    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbConstraints = new GridBagConstraints();

        panel.setBorder(IdeBorderFactory.createBorder());

        gbConstraints.insets = new Insets(4, 8, 4, 8);
        gbConstraints.gridx = 0;
        gbConstraints.weightx = 0;
        gbConstraints.gridwidth = 1;
        gbConstraints.fill = GridBagConstraints.HORIZONTAL;
        gbConstraints.anchor = GridBagConstraints.WEST;
        panel.add(new JLabel("Class name"), gbConstraints);

        gbConstraints.insets = new Insets(4, 8, 4, 8);
        gbConstraints.gridx = 1;
        gbConstraints.weightx = 1;
        gbConstraints.gridwidth = 1;
        gbConstraints.fill = GridBagConstraints.HORIZONTAL;
        gbConstraints.anchor = GridBagConstraints.WEST;
        panel.add(targetClassNameField, gbConstraints);

        targetClassNameField.getDocument().addDocumentListener(new DocumentAdapter() {
            protected void textChanged(DocumentEvent e) {
                getOKAction().setEnabled(JavaPsiFacade.getInstance(project).getNameHelper().isIdentifier(getClassName()));
            }
        });

        gbConstraints.gridx = 0;
        gbConstraints.gridy = 3;
        gbConstraints.weightx = 0;
        gbConstraints.gridwidth = 1;
        panel.add(new JLabel(CodeInsightBundle.message("dialog.create.class.destination.package.label")), gbConstraints);

        gbConstraints.gridx = 1;
        gbConstraints.weightx = 1;

        new AnAction() {
            public void actionPerformed(AnActionEvent e) {
                targetPackageField.getButton().doClick();
            }
        }.registerCustomShortcutSet(new CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK)),
                targetPackageField.getChildComponent());

        JPanel innerPanel = new JPanel(new BorderLayout());
        innerPanel.add(targetPackageField, BorderLayout.CENTER);
        panel.add(innerPanel, gbConstraints);

        return panel;
    }

    protected void doOKAction() {
        RecentsManager.getInstance(project).registerRecentEntry(RECENTS_KEY, targetPackageField.getText());
        CommandProcessor.getInstance().executeCommand(project, new OKActionRunnable(), CodeInsightBundle.message("create.directory.command"), null);
        super.doOKAction();
    }

    private String getPackageName() {
        String name = targetPackageField.getText();
        return (name != null) ? name.trim() : "";
    }

    public JComponent getPreferredFocusedComponent() {
        return targetClassNameField;
    }

    public String getClassName() {
        return targetClassNameField.getText();
    }

    public PsiDirectory getTargetDirectory() {
        return targetDirectory;
    }

    private class OKActionRunnable implements Runnable {
        @Override
        public void run() {
            final String[] errorString = new String[1];
            try {
                targetDirectory = psiHelper.getDirectoryFromModuleAndPackageName(module, getPackageName());
                if (targetDirectory == null) {
                    errorString[0] = ""; // message already reported by PackageUtil
                    return;
                }
                errorString[0] = RefactoringMessageUtil.checkCanCreateClass(targetDirectory, getClassName());
            } catch (IncorrectOperationException e) {
                errorString[0] = e.getMessage();
            }
            if (errorString[0] != null) {
                if (errorString[0].length() > 0) {
                    Messages.showMessageDialog(project, errorString[0], CommonBundle.getErrorTitle(), Messages.getErrorIcon());
                }
                return;
            }
        }
    }
}
