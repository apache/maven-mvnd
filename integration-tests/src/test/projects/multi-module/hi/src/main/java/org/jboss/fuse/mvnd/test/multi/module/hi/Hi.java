package org.jboss.fuse.mvnd.test.multi.module.hi;

import org.jboss.fuse.mvnd.test.multi.module.api.Greeting;

public class Hi implements Greeting {

    public String greet() {
        return "Hi";
    }

}
