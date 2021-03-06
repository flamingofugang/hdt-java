/**
 * File: $HeadURL: https://hdt-java.googlecode.com/svn/trunk/hdt-jena/src/org/rdfhdt/hdtjena/solver/StageMatchTripleID.java $
 * Revision: $Rev: 197 $
 * Last modified: $Date: 2013-04-12 19:39:33 +0100 (vie, 12 abr 2013) $
 * Last modified by: $Author: mario.arias $
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Contacting the authors:
 *   Mario Arias:               mario.arias@deri.org
 *   Javier D. Fernandez:       jfergar@infor.uva.es
 *   Miguel A. Martinez-Prieto: migumar2@infor.uva.es
 */
package org.rdfhdt.hdtjena.solver;


import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.jena.atlas.iterator.Iter;
import org.apache.jena.atlas.iterator.RepeatApplyIterator;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.shared.PrefixMapping;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.ExecutionContext;
import org.rdfhdt.hdt.enums.TripleComponentRole;
import org.rdfhdt.hdt.triples.IteratorTripleID;
import org.rdfhdt.hdt.triples.TripleID;
import org.rdfhdt.hdt.triples.Triples;
import org.rdfhdt.hdtjena.HDTGraph;
import org.rdfhdt.hdtjena.NodeDictionary;
import org.rdfhdt.hdtjena.bindings.BindingHDTId;
import org.rdfhdt.hdtjena.bindings.HDTId;
import org.rdfhdt.hdtjena.util.VarAppearance;



public class StageMatchTripleID extends RepeatApplyIterator<BindingHDTId>
{
	
	public static long numSearches = 0;
	
    private final NodeDictionary dictionary ;
    private final Triples triples;
    private final TripleID patternID;
    
    private final PrefixMapping prefixMap;
    
    // Variables for this tuple after substitution
    private final Var[] var = new Var[3];
    private final boolean[] varIsSO = new boolean[3];
    private final long numSharedSO;
    
    public StageMatchTripleID(HDTGraph graph, Iterator<BindingHDTId> input, Triple patternTuple, ExecutionContext execCxt, Map<Var, VarAppearance> mapVar)
    {
        super(input);
        this.dictionary = graph.getNodeDictionary();
        this.triples = graph.getHDT().getTriples();
		this.prefixMap = NodeDictionary.getMapping(execCxt);
		this.numSharedSO = graph.getHDT().getDictionary().getNshared();

        // Convert Nodes to a TripleID
        int subject=0, predicate=0, object=0;
		
		Node subjectNode = patternTuple.getSubject();
        if(!subjectNode.isVariable()) {
        	subject = dictionary.getIntID(subjectNode, prefixMap, TripleComponentRole.SUBJECT);
        } else {
        	var[0] = NodeDictionary.asVar(subjectNode);
        	varIsSO[0] = mapVar.get(var[0]).isSubjectObject();
        }
        
		Node predicateNode = patternTuple.getPredicate();
        if(!predicateNode.isVariable()) {
        	predicate = dictionary.getIntID(predicateNode, prefixMap, TripleComponentRole.PREDICATE);
        } else {
        	var[1] = NodeDictionary.asVar(predicateNode);
        }
        
		Node objectNode = patternTuple.getObject();
        if(!objectNode.isVariable()) {
        	object = dictionary.getIntID(objectNode, prefixMap, TripleComponentRole.OBJECT);
        } else {
        	var[2] = NodeDictionary.asVar(objectNode);
        	varIsSO[2] = mapVar.get(var[2]).isSubjectObject();
        }
        this.patternID = new TripleID(subject,predicate,object);
    }
    
    @Override
    protected Iterator<BindingHDTId> makeNextStage(final BindingHDTId input)
    {        
    	
    	numSearches++;
    	    	
        if(var[0]!=null) {
        	HDTId id = input.get(var[0]);
        	if(id!=null) {
        		patternID.setSubject(NodeDictionary.translate(dictionary, id, TripleComponentRole.SUBJECT));
        	}
        }
        
        if(var[1]!=null) {
        	HDTId id = input.get(var[1]);
        	if(id!=null) {
        		patternID.setPredicate(NodeDictionary.translate(dictionary, id, TripleComponentRole.PREDICATE));
        	}
        }
        
        if(var[2]!=null) {
        	HDTId id = input.get(var[2]);
        	if(id!=null) {
        		patternID.setObject(NodeDictionary.translate(dictionary, id, TripleComponentRole.OBJECT));
        	}
        }

        if(patternID.getSubject()==-1 || patternID.getPredicate()==-1 || patternID.getObject()==-1) {
        	// Not found in dictionary, no match.
        	return Iter.nullIter();
        }
        
//        System.out.println("Searching PatternID: "+patternID);
        
        // Do the search
        IteratorTripleID iterTripleIDSearch = triples.search(patternID);
        Iter<TripleID> it = Iter.iter(iterTripleIDSearch);
 
//      // FIXME: Allow a filter here.     
        
        // Filter triples where S or O need to be shared.
        if(varIsSO[0] || varIsSO[2]) {
           	it = it.filter(new Predicate<TripleID>() {
				@Override
				public boolean test(TripleID t) {
    				if(varIsSO[0] && t.getSubject()>numSharedSO) {
    					return false;
    				}
    				if(varIsSO[2] && t.getObject()>numSharedSO) {
    					return false;
    				}
    				return true;
				}
			});
        }
        
        
        // Map TripleID to BindingHDTId
        Function<TripleID, BindingHDTId> binder = new Function<TripleID, BindingHDTId>()
        {
            @Override
            public BindingHDTId apply(TripleID triple)
            {
                BindingHDTId output = new BindingHDTId(input) ;
                
                if(var[0]!=null && !output.containsKey(var[0])) {
                	output.put(var[0], new HDTId(triple.getSubject(), TripleComponentRole.SUBJECT, dictionary));
                }
                
                if(var[1]!=null && !output.containsKey(var[1])) {
                	output.put(var[1], new HDTId(triple.getPredicate(), TripleComponentRole.PREDICATE, dictionary));
                }
                
                if(var[2]!=null && !output.containsKey(var[2])) {
                	output.put(var[2], new HDTId(triple.getObject(), TripleComponentRole.OBJECT, dictionary));
                }

                return output;
            }
        };
        
        return it.map(binder);
    }
    
}
