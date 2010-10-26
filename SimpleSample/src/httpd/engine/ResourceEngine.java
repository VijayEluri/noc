package httpd.engine;

import httpd.resource.StaticResource;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.simpleframework.http.Address;
import org.simpleframework.http.resource.Resource;

import frame.Engine;

public class ResourceEngine implements Engine<Address, Resource> {
    private static final Log log = LogFactory.getLog(StaticResource.class);
    final File appHome;
    final Engine<Address, Resource> defaultEngine;
    final Engine<Address, Resource> staticEngine;

    public ResourceEngine(File appHome) {
        this(appHome,new StaticResourceEngine(appHome), new DynamicResourceEngine(appHome));
    }

    public ResourceEngine(File appHome,StaticResourceEngine staticEngine, DynamicResourceEngine dynamicEngine) {
        this.appHome = appHome;
        this.staticEngine = staticEngine;
        this.defaultEngine = dynamicEngine;

        engines = new HashMap<String, Engine<Address, Resource>>();
        this.register("js", staticEngine);
        this.register("css", staticEngine);
        this.register("images", staticEngine);
        this.register("noc", staticEngine);
        this.register("tempalte", staticEngine);
//        this.register("presentation", new PresentationResourceEngine(this.root, null));

        resources = new HashMap<String, Resource>();

    }

    final Map<String, Resource> resources;

    Map<String, Engine<Address, Resource>> engines;

    public void register(String key, Engine<Address, Resource> engine) {
        engines.put(key, engine);
    }

    @Override
    public Resource resolve(Address target) {
        String path = target.getPath().getPath();
        log.debug("Client request : " + target.toString());

        Resource o = resources.get(path);
        if (o == null) {
            o = this.make(target);
            resources.put(path, o);
            o = resources.get(path);
        }
        return o;
    }

    public Resource make(Address target) {
        log.debug("Make resource : " + target.toString());

        // TODO 需要改善性能
        String[] se = target.getPath().getSegments();
        if (se.length == 1) {
            return staticEngine.resolve(target);
        }

        Engine<Address, Resource> engine = engines.get(se[0]);
        if (engine != null) {
            return engine.resolve(target);
        }

        return defaultEngine.resolve(target);
    }
}