package com.benayn.ustyle;

import static com.benayn.ustyle.string.Strs.INDEX_NONE_EXISTS;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.primitives.Primitives.allPrimitiveTypes;
import static com.google.common.primitives.Primitives.isWrapperType;
import static com.google.common.primitives.Primitives.wrap;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.benayn.ustyle.logger.Log;
import com.benayn.ustyle.logger.Loggers;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

public final class Reflecter<T> {
	
	/**
	 * 
	 */
	protected static final Log log = Loggers.from(Reflecter.class);
	
	/**
     * <p>The inner class separator character: <code>'$' == {@value}</code>.</p>
     */
    public static final char INNER_CLASS_SEPARATOR_CHAR = '$';
	
	/**
	 * 
	 */
	private Optional<T> delegate;
	
	private Optional<Gather<Field>> fieldHolder = Optional.absent();
	private Optional<Mapper<String, Object>> nameValMap = Optional.absent();
	
	private Reflecter(T target) {
		this.delegate = Optional.fromNullable(target);
		intlFields();
	}

	/**
	 * Returns a new Reflecter instance
	 * 
	 * @param target
	 * @return
	 */
	@SuppressWarnings("unchecked") public static <T> Reflecter<T> from(T target) {
		if (Decisions.isClass().apply(target)) {
			return new Reflecter<T>((T) Suppliers2.toInstance((Class<?>) target).get());
		}
		
		return new Reflecter<T>(target);
	}
	
	/**
	 * Returns the property value to which the specified property name
	 * 
	 * @param propName
	 * @return
	 */
	public <F> F val(String propName) {
		if (nameValMap.isPresent()) {
			return nameValMap.get().get(propName);
		}
		
		return getPropVal(matchField(propName), propName);
	}
	
	/**
	 * Set the specified property value to the specified property name in this object instance
	 * 
	 * @param propName
	 * @param propVal
	 * @return
	 */
	public <V> Reflecter<T> val(String propName, V propVal) {
		setPropVal(matchField(propName), propName, propVal);
		return this;
	}
	
	/**
	 * Clone a bean based on the delegate target available property getters and setters
     * 
	 * @return
	 */
	public <Dest> Dest clones() {
		return copyTo(delegate.get().getClass());
	}
	
	/**
	 * Copy all the same property to the given object
	 * 
	 * @param dest
	 * @return
	 */
	public <Dest> Dest copyTo(Object dest) {
		return copyTo(dest, new String[]{});
	}
	
	/**
	 * Copy all the same property to the given object, except the property name in the given exclude array
	 * 
	 * @param dest
	 * @param excludes
	 * @return
	 */
	public <Dest> Dest copyTo(Object dest, String... excludes) {
		return from(dest)
				.setExchanges(exchangeProps)
				.setExchangeFuncs(exchangeFuncs)
				.setAutoExchange(autoExchange)
				.setExcludePackagePath(excludePackagePath)
				.setTrace(trace)
				.populate(asMap(), excludes).get();
	}
	
	/**
	 * Returns delegate object instance as a map, key is property name, value is property value
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked") public <V> Map<String, V> asMap() {
		return (Map<String, V>) mapper().map();
	}
	
	/**
	 * Populate the JavaBeans properties of this delegate object, based on the specified name/value pairs
	 * 
	 * @param properties
	 * @return
	 */
	public <V> Reflecter<T> populate(Map<String, V> properties) {
		return populate(properties, new String[]{});
	}
	
	/**
	 * Populate the JavaBeans properties of this delegate object, based on the specified name/value pairs
	 * 
	 * @param properties
	 * @return
	 */
	public <V> Reflecter<T> populate(Map<String, V> properties, String... excludes) {
		return populate(properties, Arrays.asList(excludes));
	}
	
	/**
	 * Populate the JavaBeans properties of this delegate object, based on the specified name/value pairs
	 * 
	 * @param properties
	 * @return
	 */
	public <V> Reflecter<T> populate(Map<String, V> properties, List<String> excludes) {
		if (Decisions.isEmpty().apply(properties)) {
			return this;
		}
		
		if (this.autoExchange) {
			exchange(Funcs.TO_BOOLEAN, booleanD);
			exchange(Funcs.TO_BYTE, byteD);
			exchange(Funcs.TO_DOUBLE, doubleD);
			exchange(Funcs.TO_FLOAT, floatD);
			exchange(Funcs.TO_INTEGER, integerD);
			exchange(Funcs.TO_LONG, longD);
			exchange(Funcs.TO_SHORT, shortD);
			exchange(Funcs.TO_DATE, dateD);
			exchange(Funcs.TO_CHARACTER, characterD);
			exchange(Funcs.TO_STRING, stringD);
			exchange(Funcs.TO_BIGDECIMAL, bigDecimalD);
			exchange(Funcs.TO_BIGINTEGER, bigIntegerD);
		}
		
		fieldHolder.get().loop(new TransformMap2ObjVal<V>(properties, excludes));
		return this;
	}

	/**
	 * Populate the JavaBeans properties of this delegate object with the random values
	 * 
	 * @return
	 */
	public Reflecter<T> populate4Test() {
		fieldHolder.get().loop(new RandomVal2ObjVal());
		return this;
	}
	
	/**
	 * Exchange the properties that matched the given decision with given exchange function which with {@link Field}
	 * 
	 * @param exchangeFunc
	 * @param decision
	 * @return
	 */
	public <I, O> Reflecter<T> exchWithField(final Function<Pair<Field, I>, O> exchangeFunc, Decision<Field> decision) {
		this.fieldHolder.get().filterAsGather(decision).loop(new Decisional<Field>() {

			@Override protected void decision(Field input) {
				exchWithField(input.getName(), input.getName(), exchangeFunc);
			}
		});
		return this;
	}
	
	/**
	 * Exchange from properties map key to delegate target field name with exchange function which with {@link Field}
	 * 
	 * @param targetFieldName
	 * @param keyFromPropMap
	 * @param exchangeFunc
	 * @return
	 */
	public <I, O> Reflecter<T> exchWithField(String targetFieldName, String keyFromPropMap, Function<Pair<Field, I>, O> exchangeFunc) {
		exchange(targetFieldName, keyFromPropMap);
		exchangeFieldFuncs.put(keyFromPropMap, exchangeFunc);
		return this;
	}
	
	/**
	 * Exchange the properties that matched the given decision with given exchange function
	 * 
	 * @param exchangeFunc
	 * @param decision
	 * @return
	 */
	public <I, O> Reflecter<T> exchange(final Function<I, O> exchangeFunc, Decision<Field> decision) {
		this.fieldHolder.get().filterAsGather(decision).loop(new Decisional<Field>() {

			@Override protected void decision(Field input) {
				exchange(input.getName(), input.getName(), exchangeFunc);
			}
		});
		return this;
	}
	
	/**
	 * Exchange properties with given exchange function
	 * 
	 * @param exchangeFunc
	 * @param inOutWithSameNameProps
	 * @return
	 */
	public <I, O> Reflecter<T> exchange(Function<I, O> exchangeFunc, String... inOutWithSameNameProps) {
		for (String propName : inOutWithSameNameProps) {
			exchange(propName, propName, exchangeFunc);
		}
		return this;
	}
	
	/**
	 * Do not auto exchange when the Field class type is primitive or wrapped primitive or Date
	 * 
	 * @return
	 */
	public Reflecter<T> noneAutoExchange() {
		this.autoExchange = Boolean.FALSE;
		return this;
	}

	/**
	 * Exchange from properties map key to delegate target field name
	 * 
	 * @param targetFieldName
	 * @param keyFromPropMap
	 * @return
	 */
	public Reflecter<T> exchange(String targetFieldName, String keyFromPropMap) {
		exchangeProps.put(targetFieldName, keyFromPropMap);
		return this;
	}
	
	/**
	 * Exchange from properties map key to delegate target field name with exchange function
	 * 
	 * @param targetFieldName
	 * @param keyFromPropMap
	 * @param exchangeFunc
	 * @return
	 */
	public <I, O> Reflecter<T> exchange(String targetFieldName, String keyFromPropMap, Function<I, O> exchangeFunc) {
		exchange(targetFieldName, keyFromPropMap);
		exchangeFuncs.put(keyFromPropMap, exchangeFunc);
		return this;
	}
	
	/**
	 * Returns delegate object instance as a {@link Mapper}
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked") public <V> Mapper<String, V> mapper() {
		if (nameValMap.isPresent()) {
			return (Mapper<String, V>) nameValMap.get();
		}
		
		Map<String, Object> fm = Maps.newHashMap();
		if (fieldHolder.isPresent()) {
			fieldHolder.get().loop(new TransformFields2Map<Object>(fm, this.trace));
		}
		nameValMap = Optional.of(Mapper.from(fm));
		
		return (Mapper<String, V>) nameValMap.get();
	}
	
	/**
	 * Returns the delegate object
	 * 
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <O> O get() {
		return (O) delegate.orNull();
	}
	
	/**
	 * Returns the Field with given property name
	 * 
	 * @param propName
	 * @return
	 */
	public Field field(String propName) {
		return matchField(propName);
	}
	
	/**
	 * Returns the fields {@link Gather}
	 * 
	 * @return
	 */
	public Gather<Field> fieldGather() {
		return this.fieldHolder.orNull();
	}
	
	/**
	 * Returns the field {@link Gather#loop(Decision)}
	 * 
	 * @param decision
	 * @return
	 */
	public Gather<Field> fieldLoop(Decision<Field> decision) {
		return fieldGather().loop(decision);
	}
	
	/**
	 * Loops the object's properties
	 * 
	 * @param decision
	 * @return
	 */
	public <V> Reflecter<T> propLoop(final Decision<Pair<Field, V>> decision) {
		fieldLoop(new Decisional<Field>() {

			@SuppressWarnings("unchecked") @Override protected void decision(Field input) {
				decision.apply((Pair<Field, V>) Pair.of(input, getPropVal(input, input.getName())));
			}
		});
		return this;
	}
	
	/**
	 * Ignore the fields in the given modifiers for the field represented
     * The <code>Modifier</code> class should be used to decode the modifiers.
     * 
	 * @param mod
	 * @return
	 */
	public Reflecter<T> ignore(final Integer mod) {
		return filter(new Decision<Field>(){
			
			@Override public boolean apply(Field input) {
				return !((mod.intValue() & input.getModifiers()) != 0);
			}
		});
	}
	
	/**
	 * Ignored the given package path to transform field to map
	 * 
	 * @param packagePath
	 * @return
	 */
	public Reflecter<T> packageIgnore(String packagePath) {
		this.excludePackagePath.add(checkNotNull(packagePath));
		return this;
	}
	
	/**
	 * Only the primitive property in delegate target join the future operate
	 * 
	 * @return
	 */
	public Reflecter<T> onlyPrimitives() {
		return filter(primitivesD);
	}
	
	/**
	 * Filter the delegate target fields with special decision
	 * 
	 * @param decision
	 */
	public Reflecter<T> filter(Decision<Field> decision) {
		this.fieldHolder.get().filter(decision);
		return this;
	}
	
	/**
	 *
	 * @param <V>
	 */
	private class TransformMap2ObjVal<V> implements Decision<Field> {
		
		V v;
		String name;
		Object propObj;
		Map<String, V> props;
		List<String> exclude;
		boolean isExchange = false;
		
		private TransformMap2ObjVal(Map<String, V> props, List<String> excludeProps) {
			this.props = props;
			this.exclude = excludeProps;
		}

		@SuppressWarnings("unchecked") @Override public boolean apply(Field input) {
			if (Modifier.isStatic(input.getModifiers())
					|| Modifier.isFinal(input.getModifiers())) {
				return true;
			}
			
			name = input.getName();
			isExchange = exchangeProps.containsKey(name) || exchangeFieldFuncs.containsKey(name);
			name = isExchange ? exchangeProps.get(name) : name;
			
			if (!props.containsKey(name)) {
				return true;
			}
			
			if (!Decisions.isEmpty().apply(exclude) && exclude.contains(name)) {
				return true;
			}
			
			v = props.get(name);
			if (null == v) {
				return true;
			}
			
			if (Map.class.isInstance(v)
					&& !Decisions.isBaseClass().apply(input.getType())) {
				propObj = Suppliers2.toInstance(input.getType()).get();
				setPropVal(input, name, from(propObj).populate((Map<String, V>) v, exclude).get());
				return true;
			}
			
			setPropVal(input, name, isExchange ? exchangeVal(input, name, v) : v);
			return true;
		}
	}
	
	/**
	 * Exchange from properties map key to delegate target field name with the given exchange map
	 * 
	 * @param exchangeMap
	 * @return
	 */
	public Reflecter<T> setExchanges(Map<String, String> exchangeMap) {
		exchangeProps.putAll(checkNotNull(exchangeMap));
		return this;
	}
	
	/**
	 * Exchange from properties map key to delegate target field name with the given exchange function map
	 * 
	 * @return
	 */
	public Reflecter<T> setExchangeFuncs(Map<String, Function<?, ?>> exchangeFuncMap) {
		exchangeFuncs.putAll(checkNotNull(exchangeFuncMap));
		return this;
	}
	
	/**
	 * Ignored the given package path to transform field to map
	 * 
	 * @param excludePackages
	 * @return
	 */
	public Reflecter<T> setExcludePackagePath(Set<String> excludePackages) {
		for (String pkg : checkNotNull(excludePackages)) {
			if (!this.excludePackagePath.contains(pkg)) {
				this.excludePackagePath.add(pkg);
			}
		}
		return this;
	}
	
	public Reflecter<T> setTrace(boolean isTrace) {
		trace = isTrace;
		return this;
	}
	
	/**
	 * 
	 * @param <V>
	 */
	private class TransformFields2Map<V> implements Decision<Field> {
		
		V v;
		String k;
		boolean traceAble;
		Map<String, V> nameValueMap;
		
		private TransformFields2Map(Map<String, V> nameValueMap, boolean traceAble) {
			this.nameValueMap = nameValueMap;
			this.traceAble = traceAble;
		}
		
		@SuppressWarnings("unchecked") @Override public boolean apply(Field input) {
			// Reject field from inner class.
			if (input.getName().indexOf(INNER_CLASS_SEPARATOR_CHAR) != INDEX_NONE_EXISTS) {
	            return true;
	        }
			
			k = input.getName();
			v = getPropVal(input, k);
			
			//IS_STRING, IS_MAP, IS_COLLECTION, IS_PRIMITIVE, IS_DATE
			if (Predicates.isNull().apply(v) 
					|| isWrapperType(v.getClass())
					|| allPrimitiveTypes().contains(v)
					|| Map.class.isInstance(v)
					|| Enum.class.isInstance(v)
					|| String.class.isInstance(v)
					|| Collection.class.isInstance(v)
					|| Date.class.isInstance(v)
					|| BigInteger.class.isInstance(v)
					|| BigDecimal.class.isInstance(v)
					|| v.getClass().isArray()) {
				
				nameValueMap.put(k, v);
				return true;
			}
			
			// Reject field from inner class.
			String clzN = v.getClass().getName();
			if (clzN.indexOf(INNER_CLASS_SEPARATOR_CHAR) != INDEX_NONE_EXISTS) {
				return true;
			}
			for (String packagePath : excludePackagePath) {
				if (c(clzN, packagePath)) {
					return true;
				}
			}
			
			if (traceAble) {
				log.info(String.format("transform %s to map for property %s.%s", clzN, delegate.get().getClass().getName(), k));
			}
			
			nameValueMap.put(k, (V) from(v).asMap());
			return true;
		}

		private boolean c(String clzN, String string) {
			return clzN.indexOf(string) != INDEX_NONE_EXISTS;
		}
	}
	
	private class RandomVal2ObjVal implements Decision<Field> {
		
		@Override public boolean apply(Field input) {
			// Reject field from inner class.
			if (input.getName().indexOf(INNER_CLASS_SEPARATOR_CHAR) != INDEX_NONE_EXISTS) {
	            return true;
	        }
			
			setPropVal(input, input.getName(), Randoms.get(input));
			return true;
		}
		
	}
	
	/**
	 * 
	 */
	private void intlFields() {
		if (!delegate.isPresent()) {
			return;
		}
		
		List<Field> fields = Lists.newLinkedList();
		Class<?> clazz = delegate.get().getClass();
		
		while (clazz != null) {
			fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
			clazz = isInnerClass() ? null : clazz.getSuperclass();
		}
		
		fieldHolder = Optional.of(Gather.from(fields));
	}
	
	/**
	 * <p>Is the delegate object class an inner class or static nested class.</p>
	 * 
	 * @return
	 */
    public boolean isInnerClass() {
        return delegate.isPresent() && delegate.get().getClass().getEnclosingClass() != null;
    }
	
	/**
	 * 
	 * @param field
	 * @param propName
	 * @return
	 */
	@SuppressWarnings("unchecked")
	private <V> V getPropVal(Field field, String propName) {
		try {
			field.setAccessible(true);
			return (V) field.get(delegate.get());
		} catch (IllegalArgumentException e) {
			log.error(String.format("get %s's value error.", propName), e);
		} catch (IllegalAccessException e) {
			log.error(String.format("get %s's value error.", propName), e);
		}
		
		return null;
	}
	
	/**
	 * 
	 * @param field
	 * @param propName
	 * @param propVal
	 */
	private <V> void setPropVal(Field field, String propName, V propVal) {
		try {
			if (this.trace) {
				log.info(String.format("set %s.%s = %s (%s)", delegate.get().getClass().getName(), 
						propName, propVal, (null == propVal ? "null" : propVal.getClass().getName())));
			}
			field.setAccessible(true);
			field.set(delegate.get(), propVal);
		} catch (IllegalArgumentException e) {
			log.error(String.format("set the value %s %s to the property %s %s error.", 
					propVal.getClass().getName(), propVal, field.getType().getName(), propName));
		} catch (IllegalAccessException e) {
			log.error(String.format("set the value %s %s to the property %s %s error.", 
					propVal.getClass().getName(), propVal, field.getType().getName(), propName));
		}
	}
	
	@SuppressWarnings("unchecked")
	private <V, I, O> V exchangeVal(Field field, String propName, V propVal) {
		if (exchangeFuncs.containsKey(propName)) {
			Function<I, O> func = (Function<I, O>) exchangeFuncs.get(propName);
			return (V) func.apply((I) propVal);
		}
		
		if (exchangeFieldFuncs.containsKey(propName)) {
			Function<Pair<Field, I>, O> func = (Function<Pair<Field, I>, O>) exchangeFieldFuncs.get(propName);
			return (V) func.apply(Pair.of(field, (I) propVal));
		}
		
		return propVal;
	}
	
	/**
	 * 
	 * @param propName
	 * @return
	 */
	private Field matchField(final String propName) {
		checkNotNull(propName);
		Field field = fieldHolder.get().find(new Decision<Field>(){
			@Override public boolean apply(Field input) {
				return input.getName().equals(propName);
			}
		}, null);
		
		return checkNotNull(field, "The property %s is not exists.", propName);
	}
	
	private Reflecter<T> setAutoExchange(boolean isAutoExchange) {
		this.autoExchange = isAutoExchange;
		return this;
	}
	
	private static final Decision<Field> booleanD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return wrap(input.getType()) == Boolean.class; }
	};
	private static final Decision<Field> byteD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return wrap(input.getType()) == Byte.class; }
	};
	private static final Decision<Field> doubleD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return wrap(input.getType()) == Double.class; }
	};
	private static final Decision<Field> floatD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return wrap(input.getType()) == Float.class; }
	};
	private static final Decision<Field> integerD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return wrap(input.getType()) == Integer.class; }
	};
	private static final Decision<Field> longD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return wrap(input.getType()) == Long.class; }
	};
	private static final Decision<Field> shortD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return wrap(input.getType()) == Short.class; }
	};
	private static final Decision<Field> characterD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return wrap(input.getType()) == Character.class; }
	};
	
	private static final Decision<Field> bigDecimalD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return input.getType() == BigDecimal.class; }
	};
	private static final Decision<Field> bigIntegerD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return input.getType() == BigInteger.class; }
	};
	private static final Decision<Field> dateD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return input.getType() == Date.class; }
	};
	private static final Decision<Field> stringD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return input.getType() == String.class; }
	};
	private static final Decision<Field> primitivesD = new Decision<Field>(){
		@Override public boolean apply(Field input) { return allPrimitiveTypes().contains(input.getType()); }
	};
	

	//<delegate target prop, populate map key>
	private Map<String, String> exchangeProps = Maps.newHashMap();
	//<populate map key, exchange function>
	private Map<String, Function<?, ?>> exchangeFuncs = Maps.newHashMap();
	private Map<String, Function<?, ?>> exchangeFieldFuncs = Maps.newHashMap();
	private boolean autoExchange = Boolean.TRUE;
	private Set<String> excludePackagePath = Sets.newHashSet("com.google.common", "ch.qos.logback", "com.benayn.ustyle");
	private boolean trace = Boolean.FALSE;
}