/*
 * Copyright 2011-2013 UnboundID Corp.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */

package com.unboundid.scim.sdk;

import com.unboundid.scim.data.BaseResource;
import com.unboundid.scim.data.ResourceFactory;
import com.unboundid.scim.schema.AttributeDescriptor;
import com.unboundid.scim.schema.CoreSchema;
import com.unboundid.scim.schema.ResourceDescriptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.unboundid.scim.sdk.StaticUtils.toLowerCase;

/**
 * This utility class may be used to generate a set of attribute
 * modifications between two SCIM resources of the same type. This is
 * especially useful for performing a PATCH request to modify a resource so it
 * matches a target resource. For example:
 *
 * UserResource target = ...
 * UserResource source = userEndpoint.getUser("someUser");
 *
 * Diff diff = Diff.generate(source, target);
 *
 * userEndpoint.update(source.getId(),
 *                     diff.getAttributesToUpdate(),
 *                     diff.getAttributesToDelete());
 *
 * @param <R> The type of resource instances the diff was generated from.
 */
public class Diff<R extends BaseResource>

{
  private final List<SCIMAttribute> attributesToUpdate;
  private final List<String> attributesToDelete;
  private final ResourceDescriptor resourceDescriptor;

  /**
   * Construct a new Diff instance.
   *
   * @param resourceDescriptor The resource descriptor of resource the diff
   *                           was generated from.
   * @param attributesToDelete The list of attributes deleted from source
   *                           resource.
   * @param attributesToUpdate The list of attributes (and their new values) to
   *                           update on the source resource.
   */
  Diff(final ResourceDescriptor resourceDescriptor,
       final List<String> attributesToDelete,
       final List<SCIMAttribute> attributesToUpdate)
  {
    this.resourceDescriptor = resourceDescriptor;
    this.attributesToDelete = attributesToDelete;
    this.attributesToUpdate = attributesToUpdate;
  }

  /**
   * Retrieves the list of attributes deleted from the source resource.
   *
   * @return The list of attributes deleted from source resource.
   */
  public List<String> getAttributesToDelete()
  {
    return attributesToDelete;
  }

  /**
   * Retrieves the list of updated attributes (and their new values) to
   * update on the source resource.
   *
   * @return The list of attributes (and their new values) to update on the
   *         source resource.
   */
  public List<SCIMAttribute> getAttributesToUpdate()
  {
    return attributesToUpdate;
  }

  /**
   * Retrieves the partial resource with the modifications that maybe sent in
   * a PATCH request.
   *
   * @param resourceFactory The ResourceFactory that should be used to create
   *                        resource instance.
   * @return The partial resource with the modifications that maybe sent in
   *         a PATCH request.
   * @throws InvalidResourceException If an error occurs.
   */
  public R getPartialResource(final ResourceFactory<R> resourceFactory)
      throws InvalidResourceException
  {
    SCIMObject scimObject = new SCIMObject();
    if(attributesToDelete != null && !attributesToDelete.isEmpty())
    {
      SCIMAttributeValue[] values =
              new SCIMAttributeValue[attributesToDelete.size()];
      for(int i = 0; i < attributesToDelete.size(); i++)
      {
        values[i] = SCIMAttributeValue.createStringValue(
                            attributesToDelete.get(i));
      }

      AttributeDescriptor subDescriptor =
              CoreSchema.META_DESCRIPTOR.getSubAttribute("attributes");

      SCIMAttribute attributes = SCIMAttribute.create(subDescriptor, values);

      SCIMAttribute meta = SCIMAttribute.create(
              CoreSchema.META_DESCRIPTOR,
              SCIMAttributeValue.createComplexValue(attributes));

      scimObject.setAttribute(meta);
    }

    if(attributesToUpdate != null)
    {
      for(SCIMAttribute attr : attributesToUpdate)
      {
        if(!attr.getAttributeDescriptor().isReadOnly())
        {
          scimObject.setAttribute(attr);
        }
      }
    }

    return resourceFactory.createResource(resourceDescriptor, scimObject);
  }

  /**
   * Generates a diff with modifications that can be applied to the source
   * resource in order to make it match the target resource.
   *
   * @param <R>    The type of the source and target resource instances.
   * @param source The source resource for which the set of modifications should
   *               be generated.
   * @param target The target resource, which is what the source resource should
   *               look like if the returned modifications are applied.
   * @param attributes The set of attributes to be compared in standard
   *                   attribute notation (ie. name.givenName). If this is
   *                   {@code null} or empty, then all attributes will be
   *                   compared.
   * @return A diff with modifications that can be applied to the source
   *         resource in order to make it match the target resource.
   */
  public static <R extends BaseResource> Diff<R> generate(
      final R source, final R target, final String... attributes)
  {
    final SCIMObject sourceObject = source.getScimObject();
    final SCIMObject targetObject = target.getScimObject();

    HashMap<String, HashMap<String, HashSet<String>>> compareAttrs = null;
    if ((attributes != null) && (attributes.length > 0))
    {
      compareAttrs = new HashMap<String, HashMap<String, HashSet<String>>>();
      for (final String s : attributes)
      {
        final AttributePath path = AttributePath.parse(s);
        final String schema = toLowerCase(path.getAttributeSchema());
        final String attrName = toLowerCase(path.getAttributeName());
        final String subAttrName = path.getSubAttributeName() == null ? null :
            toLowerCase(path.getSubAttributeName());

        HashMap<String, HashSet<String>> schemaAttrs = compareAttrs.get(schema);
        if(schemaAttrs == null)
        {
          schemaAttrs = new HashMap<String, HashSet<String>>();
          compareAttrs.put(schema, schemaAttrs);
        }
        HashSet<String> subAttrs = schemaAttrs.get(attrName);
        if(subAttrs == null)
        {
          subAttrs = new HashSet<String>();
          schemaAttrs.put(attrName, subAttrs);
        }
        if(subAttrName != null)
        {
          subAttrs.add(subAttrName);
        }
      }
    }

    final SCIMObject sourceOnlyAttrs = new SCIMObject();
    final SCIMObject targetOnlyAttrs = new SCIMObject();
    final SCIMObject commonAttrs = new SCIMObject();

    for (final String schema : sourceObject.getSchemas())
    {
      for (final SCIMAttribute attribute : sourceObject.getAttributes(schema))
      {
        if (!shouldProcess(compareAttrs, attribute, null))
        {
          continue;
        }

        sourceOnlyAttrs.setAttribute(attribute);
        commonAttrs.setAttribute(attribute);
      }
    }

    for (final String schema : targetObject.getSchemas())
    {
      for (final SCIMAttribute attribute : targetObject.getAttributes(schema))
      {
        if (!shouldProcess(compareAttrs, attribute, null))
        {
          continue;
        }

        if (!sourceOnlyAttrs.removeAttribute(
            attribute.getSchema(), attribute.getName()))
        {
          // It wasn't in the set of source attributes, so it must be a
          // target-only attribute.
          targetOnlyAttrs.setAttribute(attribute);
        }
      }
    }

    for (final String schema : sourceOnlyAttrs.getSchemas())
    {
      for (final SCIMAttribute attribute :
          sourceOnlyAttrs.getAttributes(schema))
      {
        commonAttrs.removeAttribute(attribute.getSchema(), attribute.getName());
      }
    }

    final Set<String> attributesToDelete = new HashSet<String>();
    final List<SCIMAttribute> attributesToUpdate =
        new ArrayList<SCIMAttribute>(10);

    // Delete all attributes that are only in the source object
    for (final String schema : sourceOnlyAttrs.getSchemas())
    {
      for (final SCIMAttribute sourceAttribute :
          sourceOnlyAttrs.getAttributes(schema))
      {
        deleteAttribute(compareAttrs, attributesToDelete, sourceAttribute);
      }
    }

    // Add all attributes that are only in the target object
    for (final String schema : targetOnlyAttrs.getSchemas())
    {
      for (final SCIMAttribute targetAttribute :
          targetOnlyAttrs.getAttributes(schema))
      {
        if (targetAttribute.getAttributeDescriptor().isMultiValued())
        {
          ArrayList<SCIMAttributeValue> targetValues =
              new ArrayList<SCIMAttributeValue>(
                  targetAttribute.getValues().length);
          for (SCIMAttributeValue targetValue : targetAttribute.getValues())
          {
            Map<String, SCIMAttribute> subAttrs =
                filterSubAttributes(compareAttrs, targetAttribute,
                    targetValue);
            if(!subAttrs.isEmpty())
            {
              targetValues.add(
                  SCIMAttributeValue.createComplexValue(subAttrs.values()));
            }
          }
          if(!targetValues.isEmpty())
          {
            attributesToUpdate.add(SCIMAttribute.create(
                targetAttribute.getAttributeDescriptor(), targetValues.toArray(
                new SCIMAttributeValue[targetValues.size()])));
          }
        }
        else if(targetAttribute.getValue().isComplex())
        {
          Map<String, SCIMAttribute> subAttrs =
              filterSubAttributes(compareAttrs, targetAttribute,
                  targetAttribute.getValue());
          if(!subAttrs.isEmpty())
          {
            attributesToUpdate.add(
                SCIMAttribute.create(targetAttribute.getAttributeDescriptor(),
                    SCIMAttributeValue.createComplexValue(subAttrs.values())));
          }
        }
        else
        {
          attributesToUpdate.add(targetAttribute);
        }
      }
    }

    // Add all common attributes with different values
    for (final String schema : commonAttrs.getSchemas())
    {
      for (final SCIMAttribute sourceAttribute :
          commonAttrs.getAttributes(schema))
      {
        SCIMAttribute targetAttribute =
            targetObject.getAttribute(sourceAttribute.getSchema(),
                sourceAttribute.getName());
        if (sourceAttribute.equals(targetAttribute))
        {
          continue;
        }

        if(sourceAttribute.getAttributeDescriptor().isMultiValued())
        {
          Set<SCIMAttributeValue> sourceValues =
              new LinkedHashSet<SCIMAttributeValue>(
                  sourceAttribute.getValues().length);
          Set<SCIMAttributeValue> targetValues =
              new LinkedHashSet<SCIMAttributeValue>(
                  targetAttribute.getValues().length);
          Collections.addAll(sourceValues, sourceAttribute.getValues());

          for (SCIMAttributeValue v : targetAttribute.getValues())
          {
            if (!sourceValues.remove(v))
            {
              // This value could be an added or updated value
              // TODO: Support matching on value sub-attribute if possible?
              targetValues.add(v);
            }
          }

          if(sourceValues.size() == sourceAttribute.getValues().length)
          {
            // All source values seem to have been deleted. Just delete the
            // attribute instead of listing all delete values.
            deleteAttribute(compareAttrs, attributesToDelete, sourceAttribute);
            sourceValues = Collections.emptySet();
          }

          ArrayList<SCIMAttributeValue> patchValues =
              new ArrayList<SCIMAttributeValue>(
                  sourceValues.size() + targetValues.size());
          for (SCIMAttributeValue sourceValue : sourceValues)
          {
            Map<String, SCIMAttribute> subAttrs =
                filterSubAttributes(compareAttrs, sourceAttribute, sourceValue);
            if(!subAttrs.isEmpty())
            {
              SCIMAttribute operationAttr;
              try
              {
                operationAttr = SCIMAttribute.create(
                    sourceAttribute.getAttributeDescriptor().getSubAttribute(
                        "operation"),
                    SCIMAttributeValue.createStringValue("delete"));
              }
              catch (InvalidResourceException e)
              {
                // This should never happen
                throw new IllegalStateException(e);
              }
              subAttrs.put(toLowerCase(operationAttr.getName()), operationAttr);
              patchValues.add(SCIMAttributeValue.createComplexValue(
                  subAttrs.values()));
            }
          }
          for (SCIMAttributeValue targetValue : targetValues)
          {
            // Add any new or updated target sub-attributes
            Map<String, SCIMAttribute> subAttrs =
                filterSubAttributes(compareAttrs, targetAttribute, targetValue);
            if(!subAttrs.isEmpty())
            {
              patchValues.add(SCIMAttributeValue.createComplexValue(
                              subAttrs.values()));
            }
          }
          if(!patchValues.isEmpty())
          {
            attributesToUpdate.add(SCIMAttribute.create(
                sourceAttribute.getAttributeDescriptor(), patchValues.toArray(
                new SCIMAttributeValue[patchValues.size()])));
          }
        }
        else if(sourceAttribute.getValue().isComplex())
        {
          // Remove any source only sub-attributes
          SCIMAttributeValue sourceAttributeValue =
              sourceAttribute.getValue();
          SCIMAttributeValue targetAttributeValue =
              targetAttribute.getValue();
          for (final Map.Entry<String, SCIMAttribute> e :
              filterSubAttributes(compareAttrs, sourceAttribute,
                  sourceAttributeValue).entrySet())
          {
            if(!targetAttributeValue.hasAttribute(e.getKey()))
            {
              final AttributePath path =
                  new AttributePath(sourceAttribute.getSchema(),
                      sourceAttribute.getName(), e.getValue().getName());
              attributesToDelete.add(path.toString());
            }
          }

          // Add any new or updated target sub-attributes
          Map<String, SCIMAttribute> targetSubAttrs =
              filterSubAttributes(compareAttrs, targetAttribute,
                  targetAttributeValue);
          final Iterator<Map.Entry<String, SCIMAttribute>> targetIterator =
              targetSubAttrs.entrySet().iterator();
          while(targetIterator.hasNext())
          {
            Map.Entry<String, SCIMAttribute> e = targetIterator.next();
            SCIMAttribute sourceSubAttr =
                sourceAttributeValue.getAttribute(e.getKey());
            if(sourceSubAttr != null && sourceSubAttr.equals(e.getValue()))
            {
              // This sub-attribute is the same so do not include it in the
              // patch.
              targetIterator.remove();
            }
          }
          if(!targetSubAttrs.isEmpty())
          {
            attributesToUpdate.add(SCIMAttribute.create(
                targetAttribute.getAttributeDescriptor(),
                SCIMAttributeValue.createComplexValue(
                    targetSubAttrs.values())));
          }
        }
        else
        {
          attributesToUpdate.add(targetAttribute);
        }
      }
    }

    return new Diff<R>(source.getResourceDescriptor(),
        Collections.unmodifiableList(
            new ArrayList<String>(attributesToDelete)),
        Collections.unmodifiableList(attributesToUpdate));
  }

  /**
   * Utility method to determine if an attribute should be processed when
   * generating the modifications.
   *
   * @param compareAttrs The map of attributes to be compared.
   * @param attribute The attribute to consider.
   * @param subAttribute The sub-attribute to consider or {@code null} if
   *                     not available.
   * @return {@code true} if the attribute should be processed or
   *         {@code false} otherwise.
   */
  private static boolean shouldProcess(
      final HashMap<String, HashMap<String, HashSet<String>>> compareAttrs,
      final SCIMAttribute attribute, final SCIMAttribute subAttribute)
  {
    if(compareAttrs == null)
    {
      return true;
    }

    final HashMap<String, HashSet<String>> schemaAttrs =
        compareAttrs.get(toLowerCase(attribute.getSchema()));

    if(schemaAttrs == null)
    {
      return false;
    }

    final HashSet<String> subAttrs = schemaAttrs.get(toLowerCase(
        attribute.getName()));

    if(subAttribute == null ||
        attribute.getAttributeDescriptor().getDataType() !=
            AttributeDescriptor.DataType.COMPLEX)
    {
      return subAttrs != null;
    }
    else
    {
      return subAttrs != null &&
          subAttrs.contains(toLowerCase(subAttribute.getName()));
    }
  }

  /**
   * Utility method to filter sub-attributes down to only those that should
   * be processed when generating the modifications.
   *
   * @param compareAttrs The map of attributes to be compared.
   * @param attribute The attribute to consider.
   * @param value     The complex SCIMAttributeValue to filter
   * @return A map of sub-attributes that should be included in the diff.
   */
  private static Map<String, SCIMAttribute> filterSubAttributes(
      final HashMap<String, HashMap<String, HashSet<String>>> compareAttrs,
      final SCIMAttribute attribute, final SCIMAttributeValue value)
  {
    Map<String, SCIMAttribute> filteredSubAttributes =
        new LinkedHashMap<String, SCIMAttribute>(
            value.getAttributes());
    Iterator<Map.Entry<String, SCIMAttribute>> subAttrsIterator =
        filteredSubAttributes.entrySet().iterator();
    while(subAttrsIterator.hasNext())
    {
      Map.Entry<String, SCIMAttribute> e = subAttrsIterator.next();
      if(!shouldProcess(compareAttrs, attribute, e.getValue()))
      {
        subAttrsIterator.remove();
      }
    }

    return filteredSubAttributes;
  }

  /**
   * Utility method to add an attribute and all its sub-attributes if
   * applicable to the attributesToDelete set.
   *
   * @param compareAttrs The map of attributes to be compared.
   * @param attributesToDelete The list of attributes to delete to append.
   * @param attribute The attribute to delete.
   */
  private static void deleteAttribute(
      final HashMap<String, HashMap<String, HashSet<String>>> compareAttrs,
      final Set<String> attributesToDelete, final SCIMAttribute attribute)
  {
    if(attribute.getAttributeDescriptor().getDataType() ==
        AttributeDescriptor.DataType.COMPLEX)
    {
      if(attribute.getAttributeDescriptor().isMultiValued())
      {
        for(SCIMAttributeValue sourceValue : attribute.getValues())
        {
          for(Map.Entry<String, SCIMAttribute> e :
              filterSubAttributes(compareAttrs, attribute,
                  sourceValue).entrySet())
          {
            // Skip normative sub-attributes for multi-valued attributes
            if(e.getKey().equals("type") ||
                e.getKey().equals("primary") ||
                e.getKey().equals("operation") ||
                e.getKey().equals("display") ||
                e.getKey().equals("value"))
            {
              continue;
            }

            final AttributePath path =
                new AttributePath(attribute.getSchema(),
                    attribute.getName(), e.getKey());
            attributesToDelete.add(path.toString());
          }
        }
      }
      else
      {
        for(Map.Entry<String, SCIMAttribute> e :
            filterSubAttributes(compareAttrs, attribute,
                attribute.getValue()).entrySet())
        {
          final AttributePath path =
              new AttributePath(attribute.getSchema(),
                  attribute.getName(), e.getKey());
          attributesToDelete.add(path.toString());
        }
      }
    }
    else
    {
      final AttributePath path =
          new AttributePath(attribute.getSchema(),
              attribute.getName(),
              null);
      attributesToDelete.add(path.toString());
    }
  }

}
