package com.vonhof.webi.bean;

import org.junit.Test;

import javax.inject.Inject;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


public class BeanContextTest {

    @Test
    public void can_add_thread_local_beans() throws Throwable {

        final BeanContext bc = new BeanContext();

        assertNull(bc.get(SimpleBean.class));

        SimpleBean mainBean = new SimpleBean("main");
        bc.addThreadLocal(mainBean);

        assertEquals(mainBean.hashCode(), bc.get(SimpleBean.class).hashCode());

        ThrowingThread thread = new ThrowingThread() {

            @Override
            public void runThrows() throws Throwable {
                SimpleBean proxyBean = bc.get(SimpleBean.class);

                SimpleBean threadedBean = new SimpleBean("threaded");
                bc.addThreadLocal(threadedBean);


                assertEquals(threadedBean.hashCode(), proxyBean.hashCode());
            }
        };


        thread.start();
        thread.join();
        thread.done();

        assertEquals(mainBean.hashCode(), bc.get(SimpleBean.class).hashCode());

        ThrowingThread thread2 = new ThrowingThread() {

            @Override
            public void runThrows() throws Throwable {
                SimpleBean proxyBean = bc.get(SimpleBean.class);

                proxyBean.getValue();
            }
        };

        thread2.start();
        thread2.join();
        assertEquals(IllegalStateException.class, thread2.threw.getClass());
    }

    @Test
    public void can_inject_proxied_thread_locals() throws Throwable {

        final BeanContext bc = new BeanContext();

        final SimpleBeanUser beanUser = new SimpleBeanUser();

        SimpleBean mainBean = new SimpleBean("main");
        bc.addThreadLocal(mainBean);

        bc.injectOnly(beanUser);

        assertNotNull(beanUser.getSimpleBean());

        assertEquals(mainBean.getValue(), beanUser.getSimpleBean().getValue());

        ThrowingThread thread = new ThrowingThread() {

            @Override
            public void runThrows() throws Throwable {
                SimpleBean threadedBean = new SimpleBean("threaded");
                bc.addThreadLocal(threadedBean);

                assertEquals(threadedBean.getValue(), beanUser.getSimpleBean().getValue());
            }
        };


        thread.start();
        thread.join();
        thread.done();

        assertEquals(mainBean.getValue(), beanUser.getSimpleBean().getValue());
    }

    public static class SimpleBeanUser {

        @Inject
        private SimpleBean simpleBean;

        public SimpleBean getSimpleBean() {
            return simpleBean;
        }
    }

    public abstract static class ThrowingThread extends Thread {

        public Throwable threw;


        @Override
        public void run() {
            try {
                runThrows();
            } catch (Throwable ex) {
                threw = ex;
            }
        }

        public void done() throws Throwable {
            if (threw != null) {
                throw threw;
            }
        }

        abstract public void runThrows() throws Throwable;
    }

    public static class SimpleBean {
        private final String value;


        public SimpleBean() {
            value = null;
        }


        public SimpleBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SimpleBean that = (SimpleBean) o;

            if (value != null ? !value.equals(that.value) : that.value != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
    }
}
