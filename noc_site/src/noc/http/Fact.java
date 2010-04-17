package noc.http;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletContext;

import noc.frame.Persister;
import noc.frame.Store;
import noc.frame.dbpersister.DbConfiguration;
import noc.frame.vo.Vo;
import noc.frame.vostore.VoPersisiterStore;
import noc.lang.reflect.Type;
import noc.lang.reflect.TypePersister;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.cache.WebappTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;

public class Fact {

	final String DEFINE_PATH = "define_path";
	final String BIZ_PATH = "biz_path";

	final String DATABASE_NAME = "database_name";
	final String USER_NAME = "user_name";
	final String USER_PASSWORD = "user_password";

	final String TEMPLATE_PATH = "template_path";
	final String TEMPLATE_WORK_PATH = "template_work_path";

	private TypePersister typeStore;
	private Configuration templateEngine;

	private DbConfiguration dbEngine;
	private Map<String, Store<?>> stores;
	private Map<String, Rule> rules;
	// Extension
	final static String TEMPLATE_EXTENSION = ".ftl";
	final boolean debugMode;

	public Fact(ServletContext context, boolean debugMode) {
		this.debugMode = debugMode;
		try {

			typeStore = new TypePersister(context.getRealPath("WEB-INF/lib"));
			// typeStore.appendClassPath(BIZ_PATH)
			typeStore.loadFolder(context.getInitParameter(DEFINE_PATH));
			typeStore.loadFolder(context.getInitParameter(BIZ_PATH));

			stores = new HashMap<String, Store<?>>();

			stores.put(Type.class.getName(), typeStore);

			TemplateLoader[] loaders = new TemplateLoader[] {
					new WebappTemplateLoader(context, context.getInitParameter(TEMPLATE_PATH)),
					// new WebappTemplateLoader(context,
					// context.getInitParameter(TEMPLATE_WORK_PATH)),
					new TypeTemplateLoader(context, typeStore, context.getInitParameter(TEMPLATE_PATH), context
							.getInitParameter(TEMPLATE_WORK_PATH)) };

			/* Create and adjust the configuration */
			templateEngine = new Configuration();
			templateEngine.setTemplateUpdateDelay(0);
			templateEngine.setTemplateLoader(new MultiTemplateLoader(loaders));
			templateEngine.setSharedVariable("contextPath", context.getContextPath());
			templateEngine.setObjectWrapper(new DefaultObjectWrapper());

			dbEngine = new DbConfiguration(context.getInitParameter(DATABASE_NAME),
					context.getInitParameter(USER_NAME), context.getInitParameter(USER_PASSWORD));
			dbEngine.init();

			rules = new HashMap<String, Rule>();

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// public Template getDataTemplate(String typeName) {
	// return getTemplate(typeName, "type");
	// }
	//
	// public Template getListTemplate(String typeName) {
	// return getTemplate(typeName, "list");
	// }

	private Template getTemplate(String typeName, String name) {
		try {
			String workTemplateName = typeName + "_" + name + TEMPLATE_EXTENSION;
			return templateEngine.getTemplate(workTemplateName);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

	}

	private Store<?> getStore(String typeName) {
		Store<?> store = stores.get(typeName);
		if (store == null) {

			Type de = typeStore.get(typeName);

			Persister<Vo> p = dbEngine.getPersister(Vo.class, de);
			p.prepare();
			stores.put(typeName, new VoPersisiterStore(de, p));
			store = stores.get(typeName);

			// prepareData(store, de, "sample");
		}
		return store;
	}

	public class Rule {
		protected String typeName;
		private Type type;
		private Store<?> store;
		private Template listTemplate;
		private Template editTemplate;
		private Template newTemplate;
		private Template menuTemplate;
		private Template popupTemplate;

		private Rule(String typeName, Type type, Store<?> store, Template listTemplate, Template editTemplate,
				Template newTemplate, Template menuTemplate, Template popupTemplate) {
			super();
			this.typeName = typeName;
			this.type = type;
			this.store = store;
			this.listTemplate = listTemplate;
			this.editTemplate = editTemplate;
			this.newTemplate = newTemplate;
			this.menuTemplate = menuTemplate;
			this.popupTemplate = popupTemplate;
		}

		public Type getType() {
			return type;
		}

		public Store<?> getStore() {
			return store;
		}

		public Template getListTemplate() {
			return listTemplate;
		}

		public Template getEditTemplate() {
			return editTemplate;
		}

		public Template getNewTemplate() {
			return newTemplate;
		}

		public Template getMenuTemplate() {
			return menuTemplate;
		}

		public Template getPopupTemplate() {
			return popupTemplate;
		}
	}

	public class DebugRule extends Rule {
		final Fact fact;

		private DebugRule(Fact fact, String typeName) {
			super(typeName, null, null, null, null, null, null, null);
			this.fact = fact;
		}

		public Type getType() {
			return typeStore.get(typeName);
		}

		public Store<?> getStore() {
			return fact.getStore(typeName);
		}

		public Template getListTemplate() {
			return fact.getTemplate(typeName, "list");
		}

		public Template getEditTemplate() {
			return fact.getTemplate(typeName, "type");
		}

		public Template getNewTemplate() {
			return fact.getTemplate(typeName, "type");
		}

		public Template getMenuTemplate() {
			return fact.getTemplate(typeName, "menu");
		}

		public Template getPopupTemplate() {
			return fact.getTemplate(typeName, "popup");
		}
	}

	public Rule getRule(String typeName) {
		Rule rule = rules.get(typeName);
		if (rule == null) {
			if (debugMode) {
				rule = new DebugRule(this, typeName);
			} else {
				rule = new Rule(typeName,
						typeStore.get(typeName), 
						getStore(typeName), 
						getTemplate(typeName,"list"), 
						getTemplate(typeName, "type"), 
						getTemplate(typeName, "type"), 
						getTemplate(typeName, "menu"), 
						getTemplate(typeName, "popup"));
			}

			rules.put(typeName, rule);
			rule = rules.get(typeName);
		}
		return rule;
	}

	// private Store<Type> getTypeStore() {
	// return this.typeStore;
	// }

	public void destroy() {
		dbEngine.destroy();
	}
}
