/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 2001, International
 * Business Machines, Inc., http://www.apache.org.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package org.apache.xerces.impl.v2;

import org.apache.xerces.impl.XMLErrorReporter;
import org.apache.xerces.util.DOMUtil;
import org.apache.xerces.util.XInt;
import org.apache.xerces.util.XIntPool;
import org.apache.xerces.xni.QName;
import org.w3c.dom.Element;

/**
 * A complex type definition schema component traverser.
 *
 * <complexType
 *   abstract = boolean : false
 *   block = (#all | List of (extension | restriction))
 *   final = (#all | List of (extension | restriction))
 *   id = ID
 *   mixed = boolean : false
 *   name = NCName
 *   {any attributes with non-schema namespace . . .}>
 *   Content: (annotation?, (simpleContent | complexContent |
 *            ((group | all | choice | sequence)?,
 *            ((attribute | attributeGroup)*, anyAttribute?))))
 * </complexType>
 * @version $Id$
 */

class  XSDComplexTypeTraverser extends XSDAbstractParticleTraverser {


    XSDComplexTypeTraverser (XSDHandler handler,
                             XSAttributeChecker gAttrCheck) {
        super(handler, gAttrCheck);
    }


    private static final boolean DEBUG=false;

    private static XSParticleDecl fErrorContent=null;

    private class ComplexTypeRecoverableError extends Exception {

      Object[] errorSubstText=null;
      ComplexTypeRecoverableError() {super();}
      ComplexTypeRecoverableError(String msgKey) {super(msgKey);}
      ComplexTypeRecoverableError(String msgKey, Object[] args) 
          {super(msgKey);
           errorSubstText=args;
          }
      
    }

    /**
     * Traverse local complexType declarations
     *
     * @param Element
     * @param XSDocumentInfo
     * @param SchemaGrammar
     * @return XSComplexTypeDecl
     */
    XSComplexTypeDecl traverseLocal(Element complexTypeNode,
                      XSDocumentInfo schemaDoc,
                      SchemaGrammar grammar) {


        Object[] attrValues = fAttrChecker.checkAttributes(complexTypeNode, false,
                              schemaDoc);
        String complexTypeName = genAnonTypeName(complexTypeNode);
        XSComplexTypeDecl type = traverseComplexTypeDecl (complexTypeNode,
                                 complexTypeName, attrValues, schemaDoc, grammar);
        fAttrChecker.returnAttrArray(attrValues, schemaDoc);

        return type;
    }

    /**
     * Traverse global complexType declarations
     *
     * @param Element
     * @param XSDocumentInfo
     * @param SchemaGrammar
     * @return XSComplexTypeDecXSComplexTypeDecl
     */
    XSComplexTypeDecl traverseGlobal (Element complexTypeNode,
                        XSDocumentInfo schemaDoc,
                        SchemaGrammar grammar){

        Object[] attrValues = fAttrChecker.checkAttributes(complexTypeNode, true,
                              schemaDoc);
        String complexTypeName = (String)  attrValues[XSAttributeChecker.ATTIDX_NAME];
        XSComplexTypeDecl type = traverseComplexTypeDecl (complexTypeNode,
                                 complexTypeName, attrValues, schemaDoc, grammar);
        grammar.addGlobalTypeDecl(type);
        fAttrChecker.returnAttrArray(attrValues, schemaDoc);

        return type;
    }


    private XSComplexTypeDecl traverseComplexTypeDecl(Element complexTypeDecl,
                                                      String complexTypeName,
                                                      Object[] attrValues,
                                                      XSDocumentInfo schemaDoc,
                                                      SchemaGrammar grammar) {

        Boolean abstractAtt  = (Boolean) attrValues[XSAttributeChecker.ATTIDX_ABSTRACT];
        XInt    blockAtt     = (XInt)    attrValues[XSAttributeChecker.ATTIDX_BLOCK];
        Boolean mixedAtt     = (Boolean) attrValues[XSAttributeChecker.ATTIDX_MIXED];
        XInt    finalAtt     = (XInt)    attrValues[XSAttributeChecker.ATTIDX_FINAL];

        XSComplexTypeDecl complexType = new XSComplexTypeDecl();
        complexType.fName = complexTypeName;
        complexType.fTargetNamespace = schemaDoc.fTargetNamespace;
        complexType.fBlock = blockAtt == null ?
                             schemaDoc.fBlockDefault : blockAtt.shortValue();
        complexType.fFinal = finalAtt == null ?
                             schemaDoc.fFinalDefault : finalAtt.shortValue();
        if (abstractAtt != null && abstractAtt.booleanValue())
            complexType.setIsAbstractType();


        Element child = null;

        try {
            // ---------------------------------------------------------------
            // First, handle any ANNOTATION declaration and get next child
            // ---------------------------------------------------------------
            child = checkContent( DOMUtil.getFirstChildElement(complexTypeDecl), attrValues, schemaDoc);

            // ---------------------------------------------------------------
            // Process the content of the complex type definition
            // ---------------------------------------------------------------
            if (child==null) {
              //
              // EMPTY complexType with complexContent
              //
              processComplexContent(child, complexType, mixedAtt.booleanValue(),
                                    schemaDoc, grammar);
            }
            else if (DOMUtil.getLocalName(child).equals
                    (SchemaSymbols.ELT_SIMPLECONTENT)) {
              //
              // SIMPLE CONTENT - to be done
              //
            }
            else if (DOMUtil.getLocalName(child).equals
                    (SchemaSymbols.ELT_COMPLEXCONTENT)) {
              traverseComplexContent(child, complexType, mixedAtt.booleanValue(),
                                     schemaDoc, grammar);
            }
            else {
              //
              // We must have ....
              // GROUP, ALL, SEQUENCE or CHOICE, followed by optional attributes
              // Note that it's possible that only attributes are specified.
              //
              processComplexContent(child, complexType, mixedAtt.booleanValue(),
                                    schemaDoc, grammar);
            }

       }
       catch (ComplexTypeRecoverableError e) {
         handleComplexTypeError(e.getMessage(),e.errorSubstText, complexType);
       }

       if (DEBUG) {
         System.out.println(complexType.toString());
       }
       return complexType;


    }

    private void traverseComplexContent(Element complexContentElement, 
                                        XSComplexTypeDecl typeInfo,
                                        boolean mixedOnType, XSDocumentInfo schemaDoc, 
                                        SchemaGrammar grammar) 
                                  throws ComplexTypeRecoverableError {

  
       String typeName = typeInfo.fName;
       Object[] attrValues = fAttrChecker.checkAttributes(complexContentElement, false,
                              schemaDoc);


       // -----------------------------------------------------------------------
       // Determine if this is mixed content 
       // -----------------------------------------------------------------------
       boolean mixedContent = mixedOnType; 
       Boolean mixedAtt     = (Boolean) attrValues[XSAttributeChecker.ATTIDX_MIXED];
       if (mixedAtt != null) {
         mixedContent = mixedAtt.booleanValue();
       }


       // -----------------------------------------------------------------------
       // Since the type must have complex content, set the simple type validators
       // to null
       // -----------------------------------------------------------------------
       typeInfo.fDatatypeValidator = null;

       Element complexContent = checkContent(DOMUtil.getFirstChildElement(complexContentElement), attrValues, schemaDoc);

       fAttrChecker.returnAttrArray(attrValues, schemaDoc);
       // If there are no children, return
       if (complexContent==null) {
          throw new ComplexTypeRecoverableError();
       }
          
       // -----------------------------------------------------------------------
       // The content should be either "restriction" or "extension"
       // -----------------------------------------------------------------------
       String complexContentName = complexContent.getLocalName();
       if (complexContentName.equals(SchemaSymbols.ELT_RESTRICTION))
         typeInfo.fDerivedBy = SchemaSymbols.RESTRICTION;
       else if (complexContentName.equals(SchemaSymbols.ELT_EXTENSION))
         typeInfo.fDerivedBy = SchemaSymbols.EXTENSION;
       else {
          // REVISIT - should create a msg in properties file 
          reportGenericSchemaError("ComplexType " + typeName + ": " + 
           "Child of complexContent must be restriction or extension"); 
          throw new ComplexTypeRecoverableError();   
       }
       if (DOMUtil.getNextSiblingElement(complexContent) != null) {
          // REVISIT - should create a msg in properties file 
          reportGenericSchemaError("ComplexType " + typeName + ": " + 
           "Invalid child of complexContent"); 
          throw new ComplexTypeRecoverableError();   
       }

       attrValues = fAttrChecker.checkAttributes(complexContent, false,
                              schemaDoc);
       QName baseTypeName = (QName)  attrValues[XSAttributeChecker.ATTIDX_BASE];
       fAttrChecker.returnAttrArray(attrValues, schemaDoc);


       // -----------------------------------------------------------------------
       // Need a base type.  Check that it's a complex type
       // -----------------------------------------------------------------------
       if (baseTypeName==null)  {
          // REVISIT - should create a msg in properties file 
          reportGenericSchemaError("ComplexType " + typeName + ": " + 
           "The base attribute must be specified for the restriction or extension");
          throw new ComplexTypeRecoverableError();   
       }

       XSTypeDecl type = (XSTypeDecl)fSchemaHandler.getGlobalDecl(schemaDoc, 
                                         XSDHandler.TYPEDECL_TYPE, baseTypeName);
       if (! (type instanceof XSComplexTypeDecl)) {
          // REVISIT - should create a msg in properties file 
          reportGenericSchemaError("ComplexType " + typeName + ": " + 
           "The base type must be complex");
          throw new ComplexTypeRecoverableError();   
       }
       XSComplexTypeDecl baseType = (XSComplexTypeDecl)type; 

       // -----------------------------------------------------------------------
       // Check that the base permits the derivation       
       // -----------------------------------------------------------------------
       if ((baseType.fFinal & typeInfo.fDerivedBy)!=0) {
          //REVISIT - generate error 

       }

       // -----------------------------------------------------------------------
       // Skip over any potential annotations  
       // -----------------------------------------------------------------------
       complexContent = checkContent(DOMUtil.getFirstChildElement(complexContent), 
                                     null, schemaDoc);

       // -----------------------------------------------------------------------
       // Process the content.  Note:  should I try to catch any complexType errors
       // here in order to return the attr array?   
       // -----------------------------------------------------------------------
       processComplexContent(complexContent, typeInfo, mixedContent, schemaDoc, 
                             grammar);
       
       // -----------------------------------------------------------------------
       // Compose the final content and attribute uses
       // -----------------------------------------------------------------------
       XSParticleDecl baseContent = baseType.fParticle;
       if (typeInfo.fDerivedBy==SchemaSymbols.RESTRICTION) {

          // This is an RESTRICTION

          if (typeInfo.fParticle==null && (!(baseContent==null || 
                                             baseContent.emptiable()))) {
            //REVISIT - need better error msg 
            throw new ComplexTypeRecoverableError("derivation-ok-restriction",   
             null);
          }
          if (typeInfo.fParticle!=null && baseContent==null) {
            //REVISIT - need better error msg 
            throw new ComplexTypeRecoverableError("derivation-ok-restriction",   
             null);
          }

          mergeAttributes(baseType.fAttrGrp, typeInfo.fAttrGrp, typeName, false);
          if (!typeInfo.fAttrGrp.validRestrictionOf(baseType.fAttrGrp)) {
            throw new ComplexTypeRecoverableError();
          }
          
       }
       else {

          // This is an EXTENSION

          //
          // Check if the contentType of the base is consistent with the new type
          // cos-ct-extends.1.4.2.2
          if (baseType.fContentType != XSComplexTypeDecl.CONTENTTYPE_EMPTY) {
             if (((baseType.fContentType == 
                   XSComplexTypeDecl.CONTENTTYPE_ELEMENT) &&
                   mixedContent) ||
                  ((baseType.fContentType ==
                   XSComplexTypeDecl.CONTENTTYPE_MIXED) && !mixedContent)) {

               // REVISIT - need to add a property message

               reportGenericSchemaError("cos-ct-extends.1.4.2.2.2.1: The content type of the base type " + baseTypeName + " and derived type " + 
                 typeName + " must both be mixed or element-only");
               throw new ComplexTypeRecoverableError();
             }

          }


          // Create the particle 
          if (typeInfo.fParticle == null) {
             typeInfo.fParticle = baseContent;
          }
          else {
             if (typeInfo.fParticle!=null && baseContent!=null && 
                 (typeInfo.fParticle.fType == XSParticleDecl.PARTICLE_ALL || 
                 baseType.fParticle.fType == XSParticleDecl.PARTICLE_ALL)) {
               reportGenericSchemaError("cos-all-limited.1.2:  An \"all\" model group that is part of a complex type definition must constitute the entire {content type} of the definition");
               throw new ComplexTypeRecoverableError();
             }
             XSParticleDecl temp = new XSParticleDecl();
             temp.fType = XSParticleDecl.PARTICLE_SEQUENCE;
             temp.fValue = baseContent;
             temp.fOtherValue = typeInfo.fParticle; 
             typeInfo.fParticle = temp;
          }

          mergeAttributes(baseType.fAttrGrp, typeInfo.fAttrGrp, typeName, true);

       }
       
    } // end of traverseComplexContent


    // This method merges attribute uses from the base, into the derived set. 
    // The first duplicate attribute, if any, is returned.   
    // LM: may want to merge with attributeGroup processing. 
    private void mergeAttributes(XSAttributeGroupDecl fromAttrGrp, 
                                 XSAttributeGroupDecl toAttrGrp,
                                 String typeName,
                                 boolean duplicatesAreError)  
                                 throws ComplexTypeRecoverableError {
       XSAttributeUse[] attrUseS = fromAttrGrp.getAttributeUses();
       XSAttributeUse existingAttrUse, duplicateAttrUse =  null;
       for (int i=0; i<attrUseS.length; i++) {
       	 existingAttrUse = toAttrGrp.getAttributeUse(attrUseS[i].fAttrDecl.fTargetNamespace,
           	                                   attrUseS[i].fAttrDecl.fName);
         if (existingAttrUse == null) {
    	   toAttrGrp.addAttributeUse(attrUseS[i]);
	 }
         else {
           if (duplicatesAreError) {
              //REVISIT - should create a msg in properties file 
              reportGenericSchemaError("ComplexType " + typeName + ": " + 
                "Duplicate attribute use " + existingAttrUse.fAttrDecl.fName );
              throw new ComplexTypeRecoverableError();   
           }
         }
       }
    }



    private void processComplexContent(Element complexContentChild,
                                 XSComplexTypeDecl typeInfo, 
                                 boolean isMixed,
                                 XSDocumentInfo schemaDoc, SchemaGrammar grammar)
                                 throws ComplexTypeRecoverableError {

       Element attrNode = null;
       XSParticleDecl particle = null;
       String typeName = typeInfo.fName;

       if (complexContentChild != null) {
           // -------------------------------------------------------------
           // GROUP, ALL, SEQUENCE or CHOICE, followed by attributes, if specified.
           // Note that it's possible that only attributes are specified.
           // -------------------------------------------------------------


          String childName = complexContentChild.getLocalName();

          if (childName.equals(SchemaSymbols.ELT_GROUP)) {

               particle = fSchemaHandler.fGroupTraverser.traverseLocal(complexContentChild,
                                         schemaDoc, grammar);
               attrNode = DOMUtil.getNextSiblingElement(complexContentChild);
           }
           else if (childName.equals(SchemaSymbols.ELT_SEQUENCE)) {
               particle = traverseSequence(complexContentChild,schemaDoc,grammar,
                                  NOT_ALL_CONTEXT);
               attrNode = DOMUtil.getNextSiblingElement(complexContentChild);
           }
           else if (childName.equals(SchemaSymbols.ELT_CHOICE)) {
               particle = traverseChoice(complexContentChild,schemaDoc,grammar,
                                  NOT_ALL_CONTEXT);
               attrNode = DOMUtil.getNextSiblingElement(complexContentChild);
           }
           else if (childName.equals(SchemaSymbols.ELT_ALL)) {
               particle = traverseAll(complexContentChild,schemaDoc,grammar,
                                  PROCESSING_ALL_GP);
               attrNode = DOMUtil.getNextSiblingElement(complexContentChild);
           }
           else {
               // Should be attributes here - will check below...
               attrNode = complexContentChild;
           }
       }

       typeInfo.fParticle = particle;

       // -----------------------------------------------------------------------
       // Set the content type
       // -----------------------------------------------------------------------

       if (isMixed) {
           typeInfo.fContentType = XSComplexTypeDecl.CONTENTTYPE_MIXED;
       }
       else if (typeInfo.fParticle == null)
           typeInfo.fContentType = XSComplexTypeDecl.CONTENTTYPE_EMPTY;
       else
           typeInfo.fContentType = XSComplexTypeDecl.CONTENTTYPE_ELEMENT;


       // -------------------------------------------------------------
       // Now, process attributes 
       // -------------------------------------------------------------
       if (attrNode != null) {
           if (!isAttrOrAttrGroup(attrNode)) {
              throw new ComplexTypeRecoverableError("src-ct",  
               new Object[]{typeInfo.fName});
           }
           traverseAttrsAndAttrGrps(attrNode,typeInfo.fAttrGrp,schemaDoc,grammar);
       }

       // -------------------------------------------------------------
       // REVISIT - do we need to add a template element for the type?
       // -------------------------------------------------------------


    } // end processComplexContent


    private boolean isAttrOrAttrGroup(Element e)
    {
        String elementName = e.getLocalName();

        if (elementName.equals(SchemaSymbols.ELT_ATTRIBUTE) ||
            elementName.equals(SchemaSymbols.ELT_ATTRIBUTEGROUP) ||
            elementName.equals(SchemaSymbols.ELT_ANYATTRIBUTE))
          return true;
        else
          return false;
    }

    private void traverseSimpleContentDecl(Element simpleContentDecl,
                                           XSComplexTypeDecl typeInfo) {
    }

    private void traverseComplexContentDecl(Element complexContentDecl,
                                            XSComplexTypeDecl typeInfo,
                                            boolean mixedOnComplexTypeDecl){
    }

    /*
     * Generate a name for an anonymous type
     */
    private String genAnonTypeName(Element complexTypeDecl) {

        // Generate a unique name for the anonymous type by concatenating together the
        // names of parent nodes
        // The name is quite good for debugging/error purposes, but we may want to
        // revisit how this is done for performance reasons (LM).
        String typeName;
        Element node = DOMUtil.getParent(complexTypeDecl);
        typeName="#AnonType_";
        while (node != null && (node != DOMUtil.getRoot(DOMUtil.getDocument(node)))) {
          typeName = typeName+node.getAttribute(SchemaSymbols.ATT_NAME);
          node = DOMUtil.getParent(node);
        }
        return typeName;
    }


    private void handleComplexTypeError(String messageId,Object[] args,
                                        XSComplexTypeDecl typeInfo) { 

        if (messageId!=null) {
          reportSchemaError(messageId, args); 
        }

        //
        //  Mock up the typeInfo structure so that there won't be problems during
        //  validation
        //
        typeInfo.fContentType = XSComplexTypeDecl.CONTENTTYPE_MIXED;
        typeInfo.fParticle = getErrorContent(); 

        // REVISIT - do we need to create a template element?

        return;

    }

    private static XSParticleDecl getErrorContent() {
      if (fErrorContent==null) {
        fErrorContent = new XSParticleDecl();
        fErrorContent.fType = XSParticleDecl.PARTICLE_SEQUENCE;
        XSParticleDecl particle = new XSParticleDecl();
        XSWildcardDecl wildcard = new XSWildcardDecl();
        wildcard.fProcessContents = XSWildcardDecl.WILDCARD_SKIP;
        particle.fType = XSParticleDecl.PARTICLE_WILDCARD;
        particle.fValue = wildcard;
        particle.fMinOccurs = 0;
        particle.fMaxOccurs = SchemaSymbols.OCCURRENCE_UNBOUNDED;
        fErrorContent.fValue = particle; 
        fErrorContent.fOtherValue = null;
      }
      return fErrorContent;
    }

    // HELP FUNCTIONS:
    //
    // 1. processAttributes
    // 2. processBasetTypeInfo
    // 3. AWildCardIntersection
    // 4. parseBlockSet - should be here or in SchemaHandler??
    // 5. parseFinalSet - also used by traverseSimpleType, thus should be in SchemaHandler
    // 6. handleComplexTypeError
    // and more...

/*
    REVISIT: something done in AttriubteTraverser in Xerces1. Should be done
             here in complexTypeTraverser.

        // add attribute to attr decl pool in fSchemaGrammar,
        if (typeInfo != null) {

            // check that there aren't duplicate attributes
            int temp = fSchemaGrammar.getAttributeDeclIndex(typeInfo.templateElementIndex, attQName);
            if (temp > -1) {
              //Error - ct-props-correct.4:  
            }

            // check that there aren't multiple attributes with type derived from ID
            if (dvIsDerivedFromID) {
               if (typeInfo.containsAttrTypeID())  {
                 // Error  - "ct-props-correct.5" 
               }
               typeInfo.setContainsAttrTypeID();
            }
            fSchemaGrammar.addAttDeDecl(typeInfo.templateElementIndex,
                                        attQName, attType,
                                        dataTypeSymbol, attValueAndUseType,
                                        fStringPool.toString( attValueConstraint), dv, attIsList);
        }
*/
}
