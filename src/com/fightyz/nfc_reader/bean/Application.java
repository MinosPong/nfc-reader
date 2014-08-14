package com.fightyz.nfc_reader.bean;

import com.fightyz.nfc_reader.SPEC;

import android.util.SparseArray;

public class Application {
	private final SparseArray<Object> properties = new SparseArray<Object>(); 
	
	public final void setProperty(SPEC.PROP prop, Object value) {
		properties.put(prop.ordinal(), value);
	}
	
	public final Object getProperty(SPEC.PROP prop) {
		return properties.get(prop.ordinal());
	}
	
	public final boolean hasProperty(SPEC.PROP prop) {
		return getProperty(prop) != null;
	}
	
	public final String getStringProperty(SPEC.PROP prop) {
		final Object v = getProperty(prop);
		return (v == null) ? "" : v.toString();
	}
	
	public final float getFloatProperty(SPEC.PROP prop) {
		final Object v = getProperty(prop);
		
		if(v == null)
			return Float.NaN;
		
		if(v instanceof Float) 
			return ((Float)v).floatValue();
		
		return Float.parseFloat(v.toString());
	}
}
