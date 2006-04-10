/*******************************************************************************
 * Copyright (c) 2004, 2005 Actuate Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *  Actuate Corporation  - initial API and implementation
 *******************************************************************************/
package org.eclipse.birt.report.designer.internal.ui.util;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.mozilla.javascript.CompilerEnvirons;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Node;
import org.mozilla.javascript.Parser;
import org.mozilla.javascript.ScriptOrFnNode;
import org.mozilla.javascript.Token;

/**
 * The utility class of expression, if the expression is column, return true,
 * else return false. The column format should like row.aaa , row["aaa"] or
 * row[index]
 */
public class ExpressionUtility
{
	private final static String STRING_ROW = "row";
    //the default cache size
	private final static int EXPR_CACHE_SIZE = 50;
	/**
	 * Use the LRU cache for the compiled expression.For performance reasons,
	 * The compiled expression put in a cache. Repeated compile of the same
	 * expression will then used the cached value.
	 */
	private static Map compiledExprCache = Collections.synchronizedMap( new LinkedHashMap( EXPR_CACHE_SIZE,
			(float) 0.75,
			true ) {

		private static final long serialVersionUID = 54331232145454L;

		protected boolean removeEldestEntry( Map.Entry eldest )
		{
			return size( ) > EXPR_CACHE_SIZE;
		}
	} );
	
	/**
	 * whether the expression is column reference
	 * @param expression
	 * @return
	 */
	public static boolean isColumnExpression( String expression )
	{
		boolean isColumn = false;
		if ( expression == null || expression.trim( ).length( ) == 0 )
			return isColumn;
		if ( compiledExprCache.containsKey( expression ) )
			return ( (Boolean) compiledExprCache.get( expression ) ).booleanValue( );
		Context context = Context.enter( );
		ScriptOrFnNode tree;
		try
		{
			CompilerEnvirons m_compilerEnv = new CompilerEnvirons( );
			m_compilerEnv.initFromContext( context );
			Parser p = new Parser( m_compilerEnv, context.getErrorReporter( ) );
			tree = p.parse( expression, null, 0 );
		}
		catch ( Exception e )
		{
			compiledExprCache.put( expression, Boolean.valueOf( false ) );
			return false;
		}
		finally
		{
			Context.exit( );
		}

		if ( tree.getFirstChild( ) == tree.getLastChild( ) )
		{
			// A single expression
			if ( tree.getFirstChild( ).getType( ) != Token.EXPR_RESULT
					&& tree.getFirstChild( ).getType( ) != Token.EXPR_VOID
					&& tree.getFirstChild( ).getType( ) != Token.BLOCK )
			{
				isColumn = false;
			}
			Node exprNode = tree.getFirstChild( );
			Node child = exprNode.getFirstChild( );
			assert ( child != null );
			if ( child.getType( ) == Token.GETELEM
					|| child.getType( ) == Token.GETPROP )
				isColumn = getDirectColRefExpr( child );
			else
				isColumn = false;
		}
		else
		{
			isColumn = false;
		}

		compiledExprCache.put( expression, Boolean.valueOf( isColumn ) );
		return isColumn;
	}
	
	/**
	 * replace the row[], row.xx with dataSetRow[],dataSetRow.xx
	 * 
	 * @param refNode
	 * @return
	 */
	public static String getReplacedColRefExpr( String columnStr )
	{
		if ( isColumnExpression( columnStr ) )
		{
			return columnStr.replaceFirst( "\\Qrow\\E", "dataSetRow" );
		}
		else
			return columnStr;
	}

	/**
	 * if the Node is row Node, return true
	 * 
	 * @param refNode
	 * @return
	 */
	private static boolean getDirectColRefExpr( Node refNode )
	{
		assert ( refNode.getType( ) == Token.GETPROP || refNode.getType( ) == Token.GETELEM );

		Node rowName = refNode.getFirstChild( );
		assert ( rowName != null );
		if ( rowName.getType( ) != Token.NAME )
			return false;

		String str = rowName.getString( );
		assert ( str != null );
		if ( !str.equals( STRING_ROW ) )
			return false;

		Node rowColumn = rowName.getNext( );
		assert ( rowColumn != null );

		if ( refNode.getType( ) == Token.GETPROP
				&& rowColumn.getType( ) == Token.STRING )
		{
			return true;
		}
		else if ( refNode.getType( ) == Token.GETELEM )
		{
			if ( rowColumn.getType( ) == Token.NUMBER
					|| rowColumn.getType( ) == Token.STRING )
				return true;
		}

		return false;
	}
}
