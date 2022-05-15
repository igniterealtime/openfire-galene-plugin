package com.rayo.core.xml;

import java.util.List;

public class DefaultXmlProviderManagerFactoryBean {

    private DefaultXmlProviderManager xmlProviderManager;

    public XmlProviderManager getObject() throws Exception {
        return xmlProviderManager;
    }

    public Class<?> getObjectType() {
        return XmlProviderManager.class;
    }

    public boolean isSingleton() {
        return true;
    }

    public void setProviders(List<XmlProvider> providers) {
        xmlProviderManager = new DefaultXmlProviderManager();
        for (XmlProvider provider : providers) {
            xmlProviderManager.register(provider);
        }
    }

}
