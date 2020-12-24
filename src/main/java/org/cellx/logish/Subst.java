package org.cellx.logish;

import io.vavr.collection.HashMap;
import io.vavr.collection.Map;
import io.vavr.control.Option;

import java.util.NoSuchElementException;

@SuppressWarnings("unused")
public final class Subst {
    final IdentityMap<Var, Object> map;
    final IdentityMap<Var, Map<String, Attribute>> attributes;

    protected Subst(IdentityMap<Var, Object> map, IdentityMap<Var, Map<String, Attribute>> attributes) {
        this.map = map;
        this.attributes = attributes;
    }

    public static Subst empty() {
        return new Subst(IdentityMap.empty(), IdentityMap.empty());
    }

    public Object getSome(Var v) {
        final Object value = map.lookup(v);
        if (value == null) throw new NoSuchElementException();
        return value;
    }

    public Object lookup(Var v) {
        return map.lookup(v);
    }

    public Map<String, Attribute> lookupAttribute(Var v) {
        return attributes.lookup(v);
    }

    public Subst put(Var v, Object value) {
        final IdentityMap<Var, Object> newMap = map.with(v, value);
        if (newMap == map) return this;
        else return new Subst(newMap, attributes);
    }

    public Subst remove(Var v) {
        final IdentityMap<Var, Object> newMap = map.without(v);
        final IdentityMap<Var, Map<String, Attribute>> newAttributes = attributes.without(v);
        if (newMap == map && newAttributes == attributes) return this;
        else return new Subst(newMap, newAttributes);
    }

    public Option<Attribute> getAttribute(Var v, String domain) {
        return attributes.get(v).flatMap(om -> om.get(domain));
    }


    public Subst setAttribute(Var v, String domain, Attribute attribute) {
        final Map<String, Attribute> attributeMap = attributes.lookup(v);
        final Map<String, Attribute> newAttributeMap;
        if (attributeMap == null) {
            newAttributeMap = HashMap.<String, Attribute>empty().put(domain, attribute);
        } else {
            newAttributeMap = attributeMap.put(domain, attribute);
        }
        final IdentityMap<Var, Map<String, Attribute>> newAttributes = attributes.with(v, newAttributeMap);
        if (newAttributes == attributes) return this;
        else return new Subst(map, newAttributes);
    }

    public Subst removeAttribute(Var v, String domain) {
        final Map<String, Attribute> attributeMap = attributes.lookup(v);
        if (attributeMap == null) return this;
        if (!attributeMap.containsKey(domain)) return this;
        final Map<String, Attribute> newAttributeMap = attributeMap.remove(domain);
        final IdentityMap<Var, Map<String, Attribute>> newAttributes;
        if (newAttributeMap.isEmpty()) {
            newAttributes = attributes.without(v);
        } else {
            newAttributes = attributes.with(v, newAttributeMap);
        }
        return new Subst(map, newAttributes);
    }

    public Subst removeAttributeMap(Var v) {
        final IdentityMap<Var, Map<String, Attribute>> newAttributes = attributes.without(v);
        if (newAttributes == attributes) return this;
        else return new Subst(map, newAttributes);
    }

    public Subst setAttributeMap(Var v, Map<String, Attribute> attributeMap) {
        final IdentityMap<Var, Map<String, Attribute>> newAttributes = attributes.with(v, attributeMap);
        if (newAttributes == attributes) return this;
        else return new Subst(map, newAttributes);
    }

}
