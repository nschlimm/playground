package com.mycompany.jeeapp.scope.extension;

import javax.enterprise.event.Observes;
import javax.enterprise.inject.spi.AfterBeanDiscovery;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.Extension;

public class MyCustomScopeExtension implements Extension{

    public void afterBeanDiscovery(@Observes AfterBeanDiscovery event, BeanManager manager) {
        event.addContext(new MyCustomScope());
    }
}
