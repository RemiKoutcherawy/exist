/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunFloor extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("floor", Function.BUILTIN_FUNCTION_NS),
			"Returns the largets number not greater than the value of $number." + 
			"If $number is the empty sequence, returns the empty sequence.",
			new SequenceType[] {
                new FunctionParameterSequenceType("number", Type.NUMBER, Cardinality.ZERO_OR_MORE, "The number")
            },
			new FunctionReturnSequenceType(Type.NUMBER, Cardinality.ONE, "the largets number without fraction part not greater than the value of $number"));

	public FunFloor(XQueryContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.NUMBER;
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		//if (contextItem != null)
		//	contextSequence = contextItem.toSequence();
        
		Sequence result;
        Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if (seq.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		NumericValue value = (NumericValue) seq.itemAt(0).convertTo(Type.NUMBER);
    		result = value.floor();
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;    
        
	}
}
