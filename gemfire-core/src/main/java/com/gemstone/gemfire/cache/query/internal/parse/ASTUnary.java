/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package com.gemstone.gemfire.cache.query.internal.parse;

import antlr.*;

import com.gemstone.gemfire.cache.query.internal.QCompiler;
import com.gemstone.gemfire.cache.query.internal.Support;
/**
 * 
 * @author Yogesh Mahajan
 *
 */
public class ASTUnary extends GemFireAST {	 
    private static final long serialVersionUID = -3906821390970046083L;

    /**
     * 
     * @param t
     */
	public ASTUnary(Token t) {
    super(t);
  }
  /**
   * 
   *
   */
  public ASTUnary() { }

  @Override
  public void compile(QCompiler compiler) {
    if (this.getType() == OQLLexerTokenTypes.TOK_MINUS) {
      GemFireAST child = (GemFireAST)getFirstChild();
      int tokenType = child.getType();
      if (tokenType == OQLLexerTokenTypes.NUM_INT
          || tokenType == OQLLexerTokenTypes.NUM_LONG
          || tokenType == OQLLexerTokenTypes.NUM_FLOAT
          || tokenType == OQLLexerTokenTypes.NUM_DOUBLE) {
        Support.Assert(child.getNextSibling() == null);
        child.setText('-' + child.getText());
        child.compile(compiler);
      } else {
        super.compile(compiler);
        compiler.unaryMinus();
      }
    } else {
      super.compile(compiler);
      compiler.not();
    }
  }
}
