/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.zmlx.hg4idea.action;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.util.IconUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgBranchCreateCommand;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgCommandResultHandler;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.provider.update.HgHeadMerger;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Nadya Zabrodina
 */
public class HgBranchPopupActions {

  private final Project myProject;
  private final HgRepository myRepository;

  HgBranchPopupActions(Project project, HgRepository repository) {
    myProject = project;
    myRepository = repository;
  }

  ActionGroup createActions(@Nullable DefaultActionGroup toInsert) {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
    popupGroup.addAction(new NewBranchAction(myProject, Collections.singletonList(myRepository)));

    if (toInsert != null) {
      popupGroup.addAll(toInsert);
    }

    popupGroup.addSeparator("Branches");
    List<String> branches = new ArrayList<String>(myRepository.getBranches());
    Collections.sort(branches);
    for (String branch : branches) {
      if (!branch.equals(myRepository.getCurrentBranch())) { // don't show current branch in the list
        popupGroup.add(new BranchActions(myProject, branch, myRepository));
      }
    }
    return popupGroup;
  }

  static class NewBranchAction extends DumbAwareAction {
    private final Project myProject;
    private final List<HgRepository> myRepositories;

    NewBranchAction(@NotNull Project project, @NotNull List<HgRepository> repositories) {
      super("New Branch", "Create and checkout new branch", IconUtil.getAddIcon());
      myProject = project;
      myRepositories = repositories;
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      final String name = HgUtil.getNewBranchNameFromUser(myProject, "Create New Branch");

      try {
        new HgBranchCreateCommand(myProject, HgUtil.getRootForSelectedFile(myProject), name).execute(new HgCommandResultHandler() {
          @Override
          public void process(@Nullable HgCommandResult result) {
            myProject.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(myProject, null);
            if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
              new HgCommandResultNotifier(myProject)
                .notifyError(result, "Creation  failed", "Branch creation [" + name + "] failed");
            }
          }
        });
      }
      catch (HgCommandException exception) {
        HgAbstractGlobalAction.handleException(myProject, exception);
      }
    }

    @Override
    public void update(AnActionEvent e) {
      if (anyRepositoryIsFresh()) {
        e.getPresentation().setEnabled(false);
        e.getPresentation().setDescription("Checkout of a new branch is not possible before the first commit.");
      }
    }

    private boolean anyRepositoryIsFresh() {
      for (HgRepository repository : myRepositories) {
        if (repository.isFresh()) {
          return true;
        }
      }
      return false;
    }
  }


  /**
   * Actions available for  branches.
   */
  static class BranchActions extends ActionGroup {

    private final Project myProject;
    private String myBranchName;
    @NotNull private final HgRepository mySelectedRepository;

    BranchActions(@NotNull Project project, @NotNull String branchName,
                  @NotNull HgRepository selectedRepository) {
      super("", true);
      myProject = project;
      myBranchName = branchName;
      mySelectedRepository = selectedRepository;
      getTemplatePresentation().setText(calcBranchText(), false); // no mnemonics
    }

    @NotNull
    private String calcBranchText() {
      return myBranchName;
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable AnActionEvent e) {
      return new AnAction[]{
        new UpdateToAction(myProject, mySelectedRepository, myBranchName),
        new MergeAction(myProject, mySelectedRepository, myBranchName)
      };
    }

    private static class MergeAction extends DumbAwareAction {

      private final Project myProject;
      private final HgRepository mySelectedRepository;
      private final String myBranchName;

      public MergeAction(@NotNull Project project,
                         @NotNull HgRepository selectedRepository,
                         @NotNull String branchName) {
        super("Merge");
        myProject = project;
        mySelectedRepository = selectedRepository;
        myBranchName = branchName;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        UpdatedFiles updatedFiles = UpdatedFiles.create();
        HgMergeCommand hgMergeCommand = new HgMergeCommand(myProject, mySelectedRepository.getRoot());
        hgMergeCommand.setBranch(myBranchName);
        //hgMergeCommand.setRevision(myBranchName.getHead().getChangeset());
        HgCommandResultNotifier notifier = new HgCommandResultNotifier(myProject);
        try {
          new HgHeadMerger(myProject, hgMergeCommand)
            .merge(mySelectedRepository.getRoot(), updatedFiles, HgRevisionNumber.NULL_REVISION_NUMBER);
          new HgConflictResolver(myProject, updatedFiles).resolve(mySelectedRepository.getRoot());
        }
        catch (VcsException exception) {
          if (exception.isWarning()) {
            notifier.notifyWarning("Warning during merge", exception.getMessage());
          }
          else {
            notifier.notifyError(null, "Exception during merge", exception.getMessage());
          }
        }
      }
    }

    private static class UpdateToAction extends DumbAwareAction {

      private final Project myProject;
      private final HgRepository mySelectedRepository;
      private final String myBranch;

      public UpdateToAction(@NotNull Project project,
                            @NotNull HgRepository selectedRepository,
                            @NotNull String branch) {
        super("Update To");
        myProject = project;
        mySelectedRepository = selectedRepository;
        myBranch = branch;
      }

      @Override
      public void actionPerformed(AnActionEvent e) {
        HgUpdateCommand hgUpdateCommand = new HgUpdateCommand(myProject, mySelectedRepository.getRoot());
        hgUpdateCommand.setBranch(myBranch);
        HgCommandResult result = hgUpdateCommand.execute();
        if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
          new HgCommandResultNotifier(myProject).notifyError(result, "", "Update failed");
        }
        myProject.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(myProject, null);
      }
    }
  }
}
