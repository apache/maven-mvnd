package org.jboss.fuse.mvnd.test.multi.module.hello;

import org.jboss.fuse.mvnd.test.multi.module.api.Greeting;

public class Hello implements Greeting {

    public String greet() {
        return "Hello";
    }

}
