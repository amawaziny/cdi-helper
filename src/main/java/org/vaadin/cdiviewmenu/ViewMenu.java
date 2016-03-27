package org.vaadin.cdiviewmenu;

import com.vaadin.annotations.Title;
import com.vaadin.cdi.CDIView;
import com.vaadin.cdi.UIScoped;
import com.vaadin.cdi.internal.Conventions;
import com.vaadin.navigator.Navigator;
import com.vaadin.navigator.View;
import com.vaadin.navigator.ViewChangeListener;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Resource;
import com.vaadin.server.ThemeResource;
import com.vaadin.ui.Button.ClickEvent;
import com.vaadin.ui.Button.ClickListener;
import com.vaadin.ui.Component;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.UI;
import com.vaadin.ui.themes.ValoTheme;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.qfast.vaadin.addon.ui.Button;
import org.qfast.vaadin.addon.ui.Header;
import org.qfast.vaadin.addon.ui.VerticalLayout;

import javax.annotation.PostConstruct;
import javax.enterprise.inject.Any;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.util.AnnotationLiteral;
import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;

import static com.vaadin.ui.Alignment.MIDDLE_CENTER;

/**
 * A helper to automatically create a menu from available Vaadin CDI view. Listed views should be annotated with
 * ViewMenuItem annotation to be listed here, there you can also set icon, caption etc.
 * <p>
 * You'll probably want something more sophisticated in your app, but this might be handy prototyping small CRUD apps.
 * <p>
 * By default the menu uses Valo themes responsive layout rules, but those can easily be overridden.
 */
@UIScoped
public class ViewMenu extends CssLayout {

    private static final String BUNDLE = "org.vaadin.cdihelper.menubundle";
    private static final long serialVersionUID = -6819420598414504248L;
    private final Header header = new Header(null).setHeaderLevel(3);
    private final HashMap<String, Button> nameToButton = new HashMap<>();
    @Inject
    BeanManager beanManager;
    private Button selectedButton;
    private Button active;
    private Component secondaryComponent;
    private CssLayout items;
    private List<String> allowedViews;

    public void setAllowedViews(List<String> allowedViews) {
        this.allowedViews = allowedViews;
    }

    public List<String> getAllowedViews() {
        return allowedViews;
    }

    public List<Bean<?>> getAvailableViews() {
        Set<Bean<?>> all = beanManager.getBeans(View.class, new AnnotationLiteral<Any>() {
        });

        final ArrayList<Bean<?>> list = new ArrayList<>(all.size());
        for (Bean<?> bean : all) {

            Class<?> beanClass = bean.getBeanClass();

            ViewMenuItem annotation = beanClass.getAnnotation(ViewMenuItem.class);
            CDIView cdiViewAnnotation = beanClass.getAnnotation(CDIView.class);
            if (cdiViewAnnotation != null && annotation != null && annotation.enabled()) {
                if (allowedViews != null) {
                    if (allowedViews.contains(cdiViewAnnotation.value())) {
                        list.add(bean);
                    }
                } else {
                    list.add(bean);
                }
            }
        }

        Collections.sort(list, new Comparator<Bean<?>>() {

            @Override
            public int compare(Bean<?> o1, Bean<?> o2) {
                ViewMenuItem a1 = o1.getBeanClass().
                        getAnnotation(ViewMenuItem.class);
                ViewMenuItem a2 = o2.getBeanClass().
                        getAnnotation(ViewMenuItem.class);
                if (a1 == null && a2 == null) {
                    final String name1 = getNameFor(o1.getBeanClass());
                    final String name2 = getNameFor(o2.getBeanClass());
                    return name1.compareTo(name2); // just compare names
                } else {
                    int order1 = a1 == null ? ViewMenuItem.DEFAULT : a1.order();
                    int order2 = a2 == null ? ViewMenuItem.DEFAULT : a2.order();
                    if (order1 == order2) {
                        final String name1 = getNameFor(o1.getBeanClass());
                        final String name2 = getNameFor(o2.getBeanClass());
                        return name1.compareTo(name2); // just compare names
                    } else {
                        return order1 - order2;
                    }
                }
            }
        });

        return list;
    }

    @PostConstruct
    public void init() {
        removeAllComponents();
        createHeader();

        final Button showMenu = new Button("Menu", new ClickListener() {
            @Override
            public void buttonClick(final ClickEvent event) {
                if (getStyleName().contains("valo-menu-visible")) {
                    removeStyleName("valo-menu-visible");
                } else {
                    addStyleName("valo-menu-visible");
                }
            }
        });
        showMenu.addStyleName(ValoTheme.BUTTON_PRIMARY);
        showMenu.addStyleName(ValoTheme.BUTTON_SMALL);
        showMenu.addStyleName("valo-menu-toggle");
        showMenu.setIcon(FontAwesome.LIST);
        addComponent(showMenu);

        items = new CssLayout(getAsLinkButtons(getAvailableViews()));
        items.setPrimaryStyleName("valo-menuitems");
        addComponent(items);

        addAttachListener(new AttachListener() {
            @Override
            public void attach(AttachEvent event) {
                getUI().addStyleName("valo-menu-responsive");
                if (getMenuTitle() == null) {
                    setMenuTitle(detectMenuTitle());
                }
                Navigator navigator = UI.getCurrent().getNavigator();
                if (navigator != null) {
                    String state = navigator.getState();
                    if (state == null) {
                        state = "";
                    }
                    Button b = nameToButton.get(state);
                    if (b != null) {
                        emphasisAsSelected(b);
                    }
                }
            }
        });
    }

    protected void createHeader() {
        setPrimaryStyleName("valo-menu");
        addStyleName("valo-menu-part");

        Image image = new Image();
        image.setSource(new ThemeResource("img/logo-small.png"));
        VerticalLayout headercontent = new VerticalLayout();
        headercontent.setSpacing(true);
        headercontent.setMargin(true);
        headercontent.addComponent(image, MIDDLE_CENTER);
        headercontent.addComponent(header, MIDDLE_CENTER);
        headercontent.setStyleName("valo-menu-title");
        addComponent(headercontent);
    }

    private Component[] getAsLinkButtons(List<Bean<?>> availableViews) {
        ArrayList<Button> buttons = new ArrayList<>(availableViews.size());
        for (Bean<?> viewBean : availableViews) {

            Class<?> beanClass = viewBean.getBeanClass();

            ViewMenuItem annotation = beanClass.getAnnotation(ViewMenuItem.class);
            if (annotation != null && !annotation.enabled()) {
                continue;
            }

            if (beanClass.getAnnotation(CDIView.class) != null) {
                Button button = getButtonFor(beanClass);
                CDIView view = beanClass.getAnnotation(CDIView.class);
                String viewId = view.value();
                if (CDIView.USE_CONVENTIONS.equals(viewId)) {
                    viewId = Conventions.deriveMappingForView(beanClass);
                }

                nameToButton.put(viewId, button);
                buttons.add(button);
            }
        }

        return buttons.toArray(new Button[buttons.size()]);
    }

    protected Button getButtonFor(final Class<?> beanClass) {
        final Button button = new Button(getNameFor(beanClass));
        button.setPrimaryStyleName("valo-menu-item");
        button.setIcon(getIconFor(beanClass));
        button.addClickListener(new ClickListener() {
            @Override
            public void buttonClick(ClickEvent event) {
                navigateTo(beanClass);
            }
        });
        return button;
    }

    protected void emphasisAsSelected(Button button) {
        if (selectedButton != null) {
            selectedButton.removeStyleName("selected");
        }
        button.addStyleName("selected");
        selectedButton = button;
    }

    protected Resource getIconFor(Class<?> viewType) {
        Annotation[] annotations = viewType.getDeclaredAnnotations();
        Resource icon = null;
        for (Annotation annotation : annotations) {
            Class<? extends Annotation> type = annotation.annotationType();
            if (type.getSimpleName().equalsIgnoreCase("CustomIcon")) {
                try {
                    icon = (Resource) type.getMethod("icon").invoke(annotation);
                } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
                    icon = null;
                    e.printStackTrace();
                    break;
                }
            }
        }
        if (icon == null) {
            ViewMenuItem annotation = viewType.getAnnotation(ViewMenuItem.class);
            if (annotation != null) {
                icon = annotation.icon();
            }
        }
        return icon;
    }

    public String getCaption(String key, Locale locale) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            return ResourceBundle.getBundle(BUNDLE, locale, contextClassLoader).getString(key);
        } catch (Exception ignored) {
        }
        return key;
    }

    protected String getNameFor(Class<?> viewType) {
        ViewMenuItem annotation = viewType.getAnnotation(ViewMenuItem.class);
        if (annotation != null && !annotation.title().isEmpty()) {
            return getCaption(annotation.title(), getLocale());
        }
        String simpleName = viewType.getSimpleName();
        simpleName = simpleName.replaceAll("View$", "");
        simpleName = StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(simpleName), " ");
        return simpleName;
    }

    @Override
    public Locale getLocale() {
        Locale locale = super.getLocale();
        return locale != null ? locale : Locale.US;
    }

    @Override
    public void setLocale(Locale locale) {
        super.setLocale(locale);
    }

    public void setActive(String viewId) {
        if (active != null) {
            active.setEnabled(true);
        }
        active = nameToButton.get(viewId);
        if (active != null) {
            active.setEnabled(false);
        }
    }

    public String getMenuTitle() {
        header.setSizeUndefined();
        return header.getText();
    }

    public void setMenuTitle(String menuTitle) {
        this.header.setText(menuTitle);
    }

    private String detectMenuTitle() {
        // try to dig a sane default from Title annotation in UI or class name
        final Class<? extends UI> uiClass = getUI().getClass();
        Title title = uiClass.getAnnotation(Title.class);
        if (title != null) {
            return title.value();
        } else {
            String simpleName = uiClass.getSimpleName();
            return simpleName.replaceAll("UI", "");
        }
    }

    public View navigateTo(final Class<?> viewClass) {
        CDIView cdiview = viewClass.getAnnotation(CDIView.class);
        String viewId = cdiview.value();
        if (CDIView.USE_CONVENTIONS.equals(viewId)) {
            viewId = Conventions.deriveMappingForView(viewClass);
        }
        return navigateTo(viewId);
    }

    public View navigateTo(final String viewId) {
        removeStyleName("valo-menu-visible");
        Button button = nameToButton.get(viewId);
        if (button != null) {
            final Navigator navigator = UI.getCurrent().getNavigator();

            final MutableObject<View> view = new MutableObject<>();

            ViewChangeListener l = new ViewChangeListener() {

                @Override
                public boolean beforeViewChange(
                        ViewChangeListener.ViewChangeEvent event) {
                    return true;
                }

                @Override
                public void afterViewChange(ViewChangeListener.ViewChangeEvent event) {
                    view.setValue(event.getNewView());
                }
            };

            navigator.addViewChangeListener(l);
            navigator.navigateTo(viewId);
            navigator.removeViewChangeListener(l);
            emphasisAsSelected(button);
            return view.getValue();
        }
        return null;
    }

    public void setSecondaryComponent(Component component) {
        if (secondaryComponent != component) {
            if (secondaryComponent != null) {
                removeComponent(secondaryComponent);
            }
            secondaryComponent = component;
            addComponent(component, 1);
        }
    }

    /**
     * Adds a custom button to the menu.
     *
     * @param button
     */
    public void addMenuItem(Button button) {
        button.setPrimaryStyleName("valo-menu-item");
        items.addComponent(button);
    }

}
