/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.uast;

import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.smartPointers.SmartPointerAnchorProvider;
import com.intellij.semantic.SemService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UElementSemContributorKt;
import org.jetbrains.uast.UastContextKt;

/**
 * @author yole
 */
public class UastElementAnchorProvider extends SmartPointerAnchorProvider {
  @Nullable
  @Override
  public PsiElement getAnchor(@NotNull PsiElement element) {
    if (element instanceof UElement) {
      PsiElement psi = ((UElement)element).getPsi();
      SemService.getSemService(psi.getProject())
        .setCachedSemElement(UElementSemContributorKt.UAST_SEM_KEY, psi, (UElement)element);
      return psi;
    }
    return null;
  }

  @Nullable
  @Override
  public PsiElement restoreElement(@NotNull PsiElement anchor) {
    return (PsiElement)UastContextKt.toUElement(anchor);
  }
}
