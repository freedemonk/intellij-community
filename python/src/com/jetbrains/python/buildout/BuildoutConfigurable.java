package com.jetbrains.python.buildout;

import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.NonDefaultProjectConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import com.jetbrains.django.facet.DjangoFacet;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.List;

/**
 * A silly configurable to add buildout facet configurator to PyCharm
 * User: dcheryasov
 * Date: Jul 28, 2010 4:34:58 PM
 */
public class BuildoutConfigurable implements Configurable, NonDefaultProjectConfigurable {

  private static final String BUILDOUT_LIB_NAME = "Buildout Eggs";
  private JCheckBox myEnabledCheckbox;
  private JPanel myPlaceholder;
  private JPanel myMainPanel;
  private BuildoutConfigPanel mySettingsPanel;
  private Module myModule;

  public BuildoutConfigurable(Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    myModule = modules.length == 0 ? null : modules [0];
    BuildoutFacetConfiguration config = null;
    if (myModule != null) {
      BuildoutFacet facet = BuildoutFacet.getInstance(myModule);
      if (facet != null) config = facet.getConfiguration();
    }
    if (config == null) config = new BuildoutFacetConfiguration(null);
    mySettingsPanel = new BuildoutConfigPanel(config);
    myPlaceholder.add(mySettingsPanel, BorderLayout.CENTER);
    myEnabledCheckbox.setSelected(! StringUtil.isEmptyOrSpaces(config.getScriptName()));
    myEnabledCheckbox.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        UIUtil.setEnabled(mySettingsPanel, myEnabledCheckbox.isSelected(), true);
      }
    });
    mySettingsPanel.addFocusListener(
      new FocusListener() {
        @Override
        public void focusGained(FocusEvent focusEvent) {
          switchNoticeText();
        }

        @Override
        public void focusLost(FocusEvent focusEvent) { }
      }
    );
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Buildout Support";
  }

  @Override
  public Icon getIcon() {
    return BuildoutFacetType.ourIcon;
  }

  @Override
  public String getHelpTopic() {
    return "reference-python-buildout";
  }

  @Override
  public JComponent createComponent() {
    switchNoticeText();
    return myMainPanel;
  }

  @Override
  public boolean isModified() {
    if (! myEnabledCheckbox.isEnabled()) return false;
    final BuildoutFacet facet = myModule != null? BuildoutFacet.getInstance(myModule) : null;
    final boolean got_facet = facet != null;
    if (myEnabledCheckbox.isSelected() != got_facet) return true;
    if (got_facet && myEnabledCheckbox.isSelected()) {
      return mySettingsPanel.isModified(facet.getConfiguration());
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    final BuildoutFacet facet = myModule != null? BuildoutFacet.getInstance(myModule) : null;
    final boolean got_facet = facet != null;
    boolean facet_is_desired = myEnabledCheckbox.isSelected();

    mySettingsPanel.apply();
    List<String> paths_from_script = null;
    if (facet_is_desired) {
      String script_name = mySettingsPanel.getScriptName();
      VirtualFile script_file = LocalFileSystem.getInstance().findFileByPath(script_name);
      if (script_file == null || script_file.isDirectory()) {
        throw new ConfigurationException("Invalid script file '" + script_name + "'");
      }
      paths_from_script = BuildoutFacet.extractFromScript(script_file);
      if (paths_from_script == null) {
        throw new ConfigurationException("Failed to extract paths from '" + script_file.getPresentableName() + "'");
      }
      mySettingsPanel.getConfiguration().setPaths(paths_from_script);

    }
    if (facet_is_desired && ! got_facet) addFacet(mySettingsPanel.getConfiguration());
    if (! facet_is_desired && got_facet) removeFacet(facet);
    if (facet_is_desired) attachLibrary(paths_from_script);
    else detachLibrary();
  }

  private void attachLibrary(final List<String> paths) throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        // add all paths to library
        final LibraryOrderEntry orderEntry = findEggsOrderEntry(ModuleRootManager.getInstance(myModule));
        if (orderEntry != null) {
          // update existing
          Library lib = orderEntry.getLibrary();
          if (lib != null) {
            fillLibrary(lib, paths);
            return;
          }
        }
        // create new
        final ModifiableRootModel root_model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        Library lib = LibraryTablesRegistrar
          .getInstance()
          .getLibraryTable(root_model.getProject())
          .createLibrary(BUILDOUT_LIB_NAME)
        ;
        fillLibrary(lib, paths);
        root_model.addLibraryEntry(lib);
        root_model.commit();
      }
    });
  }

  private static void fillLibrary(Library lib, List<String> paths) {
    Library.ModifiableModel modifiableModel = lib.getModifiableModel();
    for (String root : lib.getUrls(OrderRootType.CLASSES)) {
      modifiableModel.removeRoot(root, OrderRootType.CLASSES);
    }
    for (String dir : paths) modifiableModel.addRoot("file://"+dir, OrderRootType.CLASSES);
    modifiableModel.commit();
  }

  private void detachLibrary() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        // remove the library
        // TODO: refactor, see detachAppEngineModuleLibrary
        final ModifiableRootModel model = ModuleRootManager.getInstance(myModule).getModifiableModel();
        OrderEntry entry = findEggsOrderEntry(model);
        if (entry == null) {
          model.dispose();
        }
        else {
          model.removeOrderEntry(entry);
          model.commit();
        }
      }
    });
  }

  @Nullable
  private static LibraryOrderEntry findEggsOrderEntry(ModuleRootModel model) {
    // TODO: refactor, see findAppEngineOrderEntry
    final OrderEntry[] orderEntries = model.getOrderEntries();
    for (OrderEntry orderEntry : orderEntries) {
      if (orderEntry instanceof LibraryOrderEntry) {
        LibraryOrderEntry loe = (LibraryOrderEntry)orderEntry;
        if (BUILDOUT_LIB_NAME.equals(loe.getLibraryName())) {
          return loe;
        }
      }
    }
    return null;
  }

  private void addFacet(BuildoutFacetConfiguration config) {
    BuildoutFacetConfigurator.setupFacet(myModule, config);
  }

  private void removeFacet(BuildoutFacet facet) {
    final ModifiableFacetModel model = FacetManager.getInstance(myModule).createModifiableModel();
    model.removeFacet(facet);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        model.commit();
      }
    });
  }

  @Override
  public void reset() {
    // TODO: refactor, see AppEngineConfigurable
    if (myModule == null) {
      myEnabledCheckbox.setSelected(false);
      myEnabledCheckbox.setEnabled(false);
    }
    else {
      myEnabledCheckbox.setEnabled(true);
      switchNoticeText();
      final BuildoutFacet instance = BuildoutFacet.getInstance(myModule);
      if (instance != null) {
        boolean selected = ! StringUtil.isEmptyOrSpaces(instance.getConfiguration().getScriptName());
        myEnabledCheckbox.setSelected(selected);
        mySettingsPanel.setEnabled(selected);
        mySettingsPanel.reset();
      }
      else {
        myEnabledCheckbox.setSelected(false);
        mySettingsPanel.setEnabled(false);
      }
    }
  }

  private void switchNoticeText() {
    mySettingsPanel.showNoticeText(DjangoFacet.getInstance(myModule) != null);
  }

  @Override
  public void disposeUIResources() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

}
