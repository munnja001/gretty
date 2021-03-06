/*
 * gretty
 *
 * Copyright 2013  Andrey Hihlovskiy.
 *
 * See the file "license.txt" for copying and usage permission.
 */
package org.akhikhl.gretty

import ch.qos.logback.classic.selector.servlet.ContextDetachingSCL
import ch.qos.logback.classic.selector.servlet.LoggerContextFilter
import groovy.json.JsonSlurper
import org.eclipse.jetty.security.HashLoginService
import org.eclipse.jetty.security.LoginService
import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.bio.SocketConnector
import org.eclipse.jetty.util.resource.FileResource
import org.eclipse.jetty.webapp.Configuration
import org.eclipse.jetty.webapp.WebAppClassLoader
import org.eclipse.jetty.webapp.WebAppContext
import org.eclipse.jetty.xml.XmlConfiguration
import org.eclipse.jetty.webapp.WebInfConfiguration
import org.eclipse.jetty.webapp.WebXmlConfiguration
import org.eclipse.jetty.webapp.MetaInfConfiguration
import org.eclipse.jetty.webapp.FragmentConfiguration
import org.eclipse.jetty.plus.webapp.EnvConfiguration
import org.eclipse.jetty.plus.webapp.PlusConfiguration
import org.eclipse.jetty.webapp.JettyWebXmlConfiguration
import javax.servlet.DispatcherType

final class Runner extends RunnerBase {

  static void main(String[] args) {
    assert args.length != 0
    Map params = new JsonSlurper().parseText(args[0])
    new Runner(params).run()
  }

  private Runner(Map params) {
    super(params)
  }

  protected void addConfigurationClasses(webAppContext) {
    webAppContext.setConfigurations([
      new WebInfConfigurationEx(),
      new WebXmlConfiguration(),
      new MetaInfConfiguration(),
      new FragmentConfiguration(),
      new EnvConfiguration(),
      new PlusConfiguration(),
      new AnnotationConfigurationEx(params.projectClassPath),
      new JettyWebXmlConfiguration()
    ] as Configuration[])
  }

  protected void applyJettyEnvXml(webAppContext) {
    if(params.jettyEnvXml != null) {
      System.out.println "Configuring webAppContext from ${params.jettyEnvXml}"
      XmlConfiguration xmlConfiguration = new XmlConfiguration(new File(params.jettyEnvXml).toURI().toURL())
      xmlConfiguration.configure(webAppContext)
    }
  }

  protected void applyJettyXml() {
    if(params.jettyXml != null) {
      System.out.println "Configuring server from ${params.jettyXml}"
      XmlConfiguration xmlConfiguration = new XmlConfiguration(new File(params.jettyXml).toURI().toURL())
      xmlConfiguration.configure(server)
    }
  }

  protected void configureConnectors() {
    if(server.getConnectors() != null && server.getConnectors().length != 0)
      return
    System.out.println 'Auto-configuring server connectors'
    SocketConnector connector = new SocketConnector()
    // Set some timeout options to make debugging easier.
    connector.setMaxIdleTime(1000 * 60 * 60)
    connector.setSoLingerTime(-1)
    connector.setPort(params.port)
    server.setConnectors([ connector ] as Connector[])
  }

  protected void configureRealm(context) {
    if(context.getSecurityHandler().getLoginService() != null)
      return
    System.out.println 'Auto-configuring login service'
    Map realmInfo = params.realmInfo
    if(realmInfo?.realm && realmInfo?.realmConfigFile)
      context.getSecurityHandler().setLoginService(new HashLoginService(realmInfo.realm, realmInfo.realmConfigFile))
  }

  protected createServer() {
    return new Server()
  }

  protected createWebAppContext(ClassLoader classLoader) {
    WebAppContext context = new WebAppContext()
    context.setExtraClasspath(params.projectClassPath.collect { it.endsWith('.jar') ? it : (it.endsWith('/') ? it : it + '/') }.findAll { !(it =~ /.*javax\.servlet-api.*\.jar/) }.join(';'))
    context.addEventListener(new ContextDetachingSCL())
    context.addFilter(LoggerContextFilter.class, '/*', EnumSet.of(DispatcherType.REQUEST))
    return context
  }

  protected int getServerPort() {
    if(server.getConnectors() != null)
      for(Connector conn in server.getConnectors())
        return conn.getLocalPort()
    return params.port
  }
}
