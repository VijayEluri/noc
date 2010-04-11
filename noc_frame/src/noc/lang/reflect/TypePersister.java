package noc.lang.reflect;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.NotFoundException;
import javassist.bytecode.SignatureAttribute;
import noc.annotation.AutoWireByName;
import noc.annotation.Catalog;
import noc.annotation.DisplayName;
import noc.annotation.FrameType;
import noc.annotation.Inline;
import noc.annotation.PrimaryKey;
import noc.frame.Store;
import noc.lang.Bool;
import noc.lang.Name;
import data.AttrRuler;

public class TypePersister implements Store<Type> {

	ClassPool pool;
	Map<String, Type> types = new HashMap<String, Type>();
	Map<String, Type> scalas = new HashMap<String, Type>();

	public TypePersister() {
		this.pool = ClassPool.getDefault();

		Type boolType = get(Bool.class.getName());
		this.scalas.put("boolean", boolType);
		this.scalas.put(boolType.getName(), boolType);

		Type nameType = get(Name.class.getName());
		this.scalas.put("java.lang.String", nameType);
		this.scalas.put(nameType.getName(), nameType);

		this.get(Type.class.getName());
		this.get(Field.class.getName());
	}

	public TypePersister(String path) {
		try {
			this.pool = ClassPool.getDefault();

			this.pool.appendClassPath(path + "/noc_define.jar");
			this.pool.appendClassPath(path + "/noc_frame.jar");

			Type boolType = get(Bool.class.getName());
			this.scalas.put("boolean", boolType);
			this.scalas.put(boolType.getName(), boolType);

			Type nameType = get(Name.class.getName());
			this.scalas.put("java.lang.String", nameType);
			this.scalas.put(nameType.getName(), nameType);

			this.get(Type.class.getName());
			this.get(Field.class.getName());
			this.get(AttrRuler.class.getName());
		} catch (NotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	public TypePersister(ClassPool pool) {
		this.pool = pool;
	}

	public void loadJar(String path) {
		try {
			pool.appendClassPath(path);
			assert (path.endsWith(".jar"));

			File f = new File(path);
			if (!f.exists())
				return;

			JarFile jf = new JarFile(path);
			Enumeration<JarEntry> en = jf.entries();
			while (en.hasMoreElements()) {
				String name = en.nextElement().getName();
				if (name.endsWith(".class")) {
					name.substring(0, name.length() - ".class".length()).replace('\\', '.').replace('/', '.');
					this.get(name);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void appendClassPath(String path) {
		try {
			pool.appendClassPath(path);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void loadFolder(String path) {
		try {
			pool.appendClassPath(path);
			File f = new File(path);
			loadFolder(f, f);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void loadFolder(File root, File d) {
		try {
			if (!d.exists() || !d.isDirectory())
				return;

			for (File f : d.listFiles()) {
				if (f.isFile() && f.getName().endsWith(".class")) {
					String name = f.getPath().substring(root.getPath().length() + 1);

					name = name.substring(0, name.length() - ".class".length()).replace('\\', '.').replace('/', '.');

					this.get(name);
				} else if (f.isDirectory()) {
					loadFolder(root, f);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override public Type get(String name) {
		try {
			Type type = types.get(name);
			if (type == null) {
				CtClass clz = pool.get(name);
				return decorateType(clz);
			}
			return type;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected Type decorateType(CtClass clz) throws ClassNotFoundException, NotFoundException {
		Type declaringType = null;
		Object an = null;

		String name = clz.getName();

		// Handle displayName
		String displayName = (an = clz.getAnnotation(DisplayName.class)) != null ? ((DisplayName) an).value() : name;

		// Handle Frame Type
		boolean frameType = clz.getAnnotation(FrameType.class) != null;

		// Handle isScala
		boolean isScala = false;
		isScala = clz.isPrimitive() ? true : isScala;
		isScala = clz.getPackageName() != null && clz.getPackageName().equals(Name.class.getPackage().getName()) ? true
				: isScala;
		isScala = clz.getPackageName() != null && clz.getPackageName().startsWith("java") ? true : isScala;

		if (isScala) {
			Type type = new Type(name, displayName, new ArrayList<Field>(0), isScala, frameType, null);
			scalas.put(name, type);
			return type;
		}

		// Handle declaringType
		CtClass declaringClazz = clz.getDeclaringClass();
		if (declaringClazz != null) {
			declaringType = this.get(declaringClazz.getName());
		}

		// Construct type
		CtField[] cfs = clz.getFields();
		List<Field> fs = new ArrayList<Field>();
		Type type = new Type(name, displayName, fs, isScala, frameType, declaringType);

		types.put(name, type);
		type = types.get(name);
		
		for (int i = 0; i < cfs.length; i++) {
			fs.add(decorateField(type, cfs[i]));
		}

		
		return type;
	}

	// TODO
	protected Type getWithScalas(String typeName, CtClass ctType) {
		try {
			Type type = scalas.get(typeName);
			type = type == null ? types.get(typeName) : type;
			 if (type == null) {
			 type = decorateType(ctType);
			 }
			return type;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	protected Field decorateField(Type parent, CtField ctField) throws ClassNotFoundException, NotFoundException {
		Object an = null;

		if (ctField.getName().equals("this$0")) {
			System.out.println(parent.getName() + " -> " +  ctField.getName());
		}

		String name = ctField.getName();

		/* Handle displayName */
		String displayName = (an = ctField.getAnnotation(DisplayName.class)) != null ? ((DisplayName) an).value()
				: name;

		/* Handle type */
		CtClass typeClazz = ctField.getType();

		// boolean isArray =

		Type type = null;
		boolean array = false;

		/* construct field */
		CtClass actualClass;

		if (typeClazz.isArray()) {
			array = true;
			actualClass = typeClazz.getComponentType();
		} else if (typeClazz.getName().equals(java.util.List.class.getName())
				|| typeClazz.getName().equals(noc.lang.List.class.getName())) {
			// Generic field
			array = true;
			actualClass = pool.get(decorateActualTypeArguments(ctField).get(0));
		} else {
			actualClass = typeClazz;
		}

		if (actualClass.getName().equalsIgnoreCase(parent.getName())) {
			type = parent;
		} else {
			type = getWithScalas(actualClass.getName(),actualClass);
		}

		assert type != null;

		Field field = new Field(name, displayName, type);
		field.setArray(array);

		// Handle primaryKey
		field.setPrimaryKey(type.scala && check(ctField, actualClass, PrimaryKey.class));
		field.setInline(!type.scala && check(ctField, actualClass, Inline.class));
		field.setCatalog(type.scala && check(ctField, actualClass, Catalog.class));

		// boolean required = check(ctField, Data.Required.class);
		// boolean basicInfo = check(ctField, Data.BasicInfo.class);
		// boolean detailInfo = check(ctField, Data.DetailInfo.class);

		field.setInline(field.inline
				|| (type.getDeclaringType() != null && parent.getName().equals(type.getDeclaringType().getName())));

		return field;
	}

	protected boolean check(CtField ctField, CtClass ctType, Class<? extends Annotation> anClz)
			throws ClassNotFoundException, NotFoundException {
		boolean succeed = false;
		Annotation an = null;

		an = anClz.getAnnotation(AutoWireByName.class);
		if (an != null) {
			AutoWireByName au = (AutoWireByName) an;
			if (au.value().indexOf(ctField.getName() + ";") >= 0) {
				succeed = true;
			}
		}

		succeed = ctType.getAnnotation(anClz) != null ? true : succeed;
		succeed = ctField.getAnnotation(anClz) != null ? true : succeed;

		return succeed;
	}

	protected ArrayList<String> decorateActualTypeArguments(CtField v) {
		SignatureAttribute s = (SignatureAttribute) v.getFieldInfo().getAttribute(SignatureAttribute.tag);
		assert s != null;

		String sig = s.getSignature();
		ArrayList<String> params = new ArrayList<String>();

		int pos = 0;

		assert sig.charAt(pos) == 'L';

		pos++;

		int start = pos;
		while (sig.charAt(++pos) != '<')
			;

		String typename = sig.substring(start, pos).replace('/', '.');

		assert typename.equals(noc.lang.List.class.getName());
		pos++;

		do {
			String pam;
			if (sig.charAt(pos) != 'L')
				break;

			pos++;

			start = pos;
			while (sig.charAt(++pos) != ';')
				;

			pam = sig.substring(start, pos).replace('/', '.');
			params.add(pam);
			pos++;
		} while (sig.charAt(pos) != '>');

		return params;
	}

	@Override public List<Type> list() {
		ArrayList<Type> types = new ArrayList<Type>();
		for (Type type : this.types.values()) {
			types.add(type);
		}
		return types;
	}

	public List<Type> listScala() {
		ArrayList<Type> types = new ArrayList<Type>();
		for (Type type : this.scalas.values()) {
			types.add(type);
		}
		return types;
	}

	@Override public Type put(Type v) {
		throw new UnsupportedOperationException(v.toString());
	}

}
