TinyDI
======

[![Build Status](https://github.com/zhgzhg/TinyDI/actions/workflows/build.yml/badge.svg)](https://github.com/zhgzhg/TinyDI/actions/workflows/build.yml)
[![Coverage](https://raw.githubusercontent.com/zhgzhg/TinyDI/badges/jacoco.svg)](https://github.com/zhgzhg/TinyDI/actions/workflows/build.yml)

Minimalistic, annotation-based Java library for dependency injection. Compatible with the traditional JVM, Android, GraalVM, etc.

Features
--------

 * Constructor-based dependency injection
 * Singleton, and prototype DI scopes
 * Bean akin components which can register, or be injected with components
 * Ability to do classpath scanning of the eligible for DI components
 * Support for programmatic registration of additional components
 * Runtime or build time component scanning, allowing DI on platforms with limited reflection capabilities (Android, GraalVM native images, etc.)
 * Simple to learn and use

TinyDI - Usage Example
----------------------

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
  
  /** A prototype component called 'currentTime' */
  @Recorded(value = "currentTime", scope = ScopeDI.PROTOTYPE)
  Date currDate() { return new Date(); }
}


/** Interface contract for printing an app's version and current date */
interface Informer {
  void printInfo();
}

/** A component which will be injected with the 'Recorded' component instances or possibly other 'Supervised' ones. */
@Supervised
class AppInfo implements Informer {
  private String version;
  private Date currDt;
  
  /**
   * Constructor which will be called automatically by TinyDI. If several eligible for injection
   * parameters of the same data type exist the @KnownAs annotation can be used to specify a concrete one.
   * @param appVersion Will be injected, taken from the Config class.
   * @param ct Will be injected, taken from the Config class. The KnownAs annotation is not mandatory in
   *           this example, but it's there for demonstration purposes. 
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
public class Main implements EntryPoint {

  public static void main(String[] args) {

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
* Primitive types are always converted to their wrappers (for e.g. int -> Integer, long -> Long, etc...)
* Nulls are not considered as valid dependency injection values
* Android, and GraalVM native images require serialization of the component scanning to be saved at build time 

Further Reading
---------------

Consult with the [wiki](https://github.com/zhgzhg/TinyDI/wiki) and the [API documentation](https://zhgzhg.github.io/TinyDI/)
for more information.
