package com.vonhof.webi.bean;

import org.junit.Test;

import javax.inject.Inject;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;


public class BeanContextTest {

    @Test
    public void can_add_thread_local_beans() throws Throwable {

        final BeanContext bc = new BeanContext();

        assertNull(bc.get(SimpleLocalBean.class));

        SimpleLocalBean mainBean = new SimpleLocalBean("main");
        bc.add(mainBean);

        assertEquals(mainBean.hashCode(), bc.get(SimpleLocalBean.class).hashCode());

        ThrowingThread thread = new ThrowingThread() {

            @Override
            public void runThrows() throws Throwable {
                SimpleLocalBean proxyBean = bc.get(SimpleLocalBean.class);

                SimpleLocalBean threadedBean = new SimpleLocalBean("threaded");
                bc.add(threadedBean);


                assertEquals(threadedBean.hashCode(), proxyBean.hashCode());
            }
        };


        thread.start();
        thread.join();
        thread.done();

        assertEquals(mainBean.hashCode(), bc.get(SimpleLocalBean.class).hashCode());

        ThrowingThread thread2 = new ThrowingThread() {

            @Override
            public void runThrows() throws Throwable {
                SimpleLocalBean proxyBean = bc.get(SimpleLocalBean.class);

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

        SimpleLocalBean mainBean = new SimpleLocalBean("main");
        bc.add(mainBean);

        bc.injectOnly(beanUser);

        assertNotNull(beanUser.getSimpleLocalBean());

        assertEquals(mainBean.getValue(), beanUser.getSimpleLocalBean().getValue());

        ThrowingThread thread = new ThrowingThread() {

            @Override
            public void runThrows() throws Throwable {
                SimpleLocalBean threadedBean = new SimpleLocalBean("threaded");
                bc.add(threadedBean);

                assertEquals(threadedBean.getValue(), beanUser.getSimpleLocalBean().getValue());
            }
        };


        thread.start();
        thread.join();
        thread.done();

        assertEquals(mainBean.getValue(), beanUser.getSimpleLocalBean().getValue());
    }

    @Test
    public void can_inject_variables_into_bean() throws Throwable {

        final BeanContext bc = new BeanContext();

        final SimpleBeanContainer beanContainer = new SimpleBeanContainer();

        assertNull(beanContainer.getSimpleBean());

        SimpleBean simpleBean = new SimpleBean();
        bc.add(simpleBean);

        bc.injectOnly(beanContainer);

        assertNotNull(beanContainer.getSimpleBean());

        assertEquals(false, simpleBean.isState());


        simpleBean.setState(true);

        assertTrue(beanContainer.getSimpleBean().isState());
    }

    public static class SimpleBean {
        private boolean state;

        public boolean isState() {
            return state;
        }

        public void setState(boolean state) {
            this.state = state;
        }
    }

    public static class SimpleBeanContainer {

        @Inject
        private SimpleBean simpleBean;

        public SimpleBean getSimpleBean() {
            return simpleBean;
        }
    }

    public static class SimpleBeanUser {

        @Inject
        private SimpleLocalBean simpleLocalBean;

        public SimpleLocalBean getSimpleLocalBean() {
            return simpleLocalBean;
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

    @BeanScope(BeanScope.Type.LOCAL)
    public static class SimpleLocalBean {
        private final String value;


        public SimpleLocalBean() {
            value = null;
        }


        public SimpleLocalBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SimpleLocalBean that = (SimpleLocalBean) o;

            if (value != null ? !value.equals(that.value) : that.value != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return value != null ? value.hashCode() : 0;
        }
    }
}
