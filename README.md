TinyDI
======

Minimalistic, annotation-based library for dependency injection. Compatible with the traditional JVM, Android, GraalVM, etc.

Features
--------

 * Constructor-based dependency injection
 * Singleton, and prototype DI scopes
 * Bean akin components which can register, or be injected with components
 * Automatic classpath scanning of the eligible for DI components
 * Support for programmatic registration of additional components
 * Runtime or build time component scanning, allowing DI on platforms with limited reflection capabilities (Android, GraalVM native images, etc.)
 * Simple to learn and use

TinyDI Usage Example
--------------------

```
package tinydi.helloworld;

import com.github.zhgzhg.tinydi.TinyDI;
import com.github.zhgzhg.tinydi.components.*;
import com.github.zhgzhg.tinydi.meta.annotations.*;
import com.github.zhgzhg.tinydi.meta.enums.ScopeDI;

import java.util.Date;

/** Component responsible for the initialization of other core for the app components. */
@Registrar
class Config {

  /** A singleton component called 'appVersion' which is a string */
  @Recorded
  String appVersion() { return "1.0.0"; }
  
  /** A prototype component called 'currentTime' which is an instance of java.util.Date */
  @Recorded(value = "currentTime", scope = ScopeDI.PROTOTYPE)
  Date currTime() { return new Date(); }
}

/** Interface contract for printing an app's version and current date */
interface Informer {
  void printInfo();
}

/**
 * A component which will be injected with some 'recorded' component instances from above.
 * It can be also injected into other supervised components
 */
@Supervised
class AppInfo implements Informer {
  private String version;
  private Date currDt;
  
  /**
   * Constructor which will be called automatically by TinyDI. Its parameters are what's being injected.
   * In case several parameters of the same data type exist the @KnownAs annotation can be used to specify a concrete one.
   * @param appVersion Will be injected, taken from the Config class.
   * @param ct Will be injected, taken from the Config class. The KnownAs annotation is not mandatory in this example, but it's there for demonstration purposes. 
   */
  public AppInfo(String appVersion, @KnownAs("currentTime") Date ct) { 
    this.version = appVersion;
    this.currDt = ct;
  }
  
  @Override
  public void printInfo() {
    System.out.printf("App (%s) - v%s%n", this.currDt, version);
  }
}

/** The entrypoint of the Java app, and also in this example of the dependency injected app too. */
@Supervised
public class Main implements Entrypoint {
  public void main(String[] args) {
    TinyDI.config()
      .basePackages(Main.class.getPackageName())
      .configure()
      .run();
  }
  
  private Informer informer;
  public Main(Informer informer) {
    this.informer = informer;
  }
  
  /** The entry point of the DI-ed app when everything is initialised */
  @Override
  public void run() {
    this.informer.printInfo();
  }
}

```

Limitations
-----------

* Only dependency injection via constructor is supported
  * You may use Lombok to reduce the boilerplate code 
* Primitive types are always converted to their wrappers
* Nulls are not considered as valid dependency injection values

Further Reading
---------------

More information can be found in the Wiki.

    

