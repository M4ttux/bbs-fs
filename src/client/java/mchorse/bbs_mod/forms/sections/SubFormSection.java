package mchorse.bbs_mod.forms.sections;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.forms.FormCategories;
import mchorse.bbs_mod.forms.categories.FormCategory;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.l10n.keys.IKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class SubFormSection extends FormSection
{
    /** The icon every category of this section wears in a form list. */
    protected abstract Icon getIcon();

    protected Map<String, FormCategory> categories = new LinkedHashMap<>();

    public SubFormSection(FormCategories parent)
    {
        super(parent);
    }

    protected abstract IKey getTitle();

    protected abstract Form create(String key);

    protected FormCategory createCategory(IKey uiKey, String id)
    {
        return new FormCategory(uiKey, this.parent.preferences.visible(id)).icon(this.getIcon());
    }

    protected abstract boolean isEqual(Form form, String key);

    protected IKey getCategoryTitle(String folderPath, boolean isRootSegment)
    {
        if (folderPath.isEmpty())
        {
            return this.getTitle();
        }

        if (isRootSegment)
        {
            return IKey.comp(Arrays.asList(this.getTitle(), IKey.constant(" (" + folderPath + ")")));
        }

        int slash = folderPath.lastIndexOf('/');

        return IKey.constant(slash >= 0 ? folderPath.substring(slash + 1) : folderPath);
    }

    protected String getKey(String key)
    {
        int slash = key.lastIndexOf('/');

        return slash >= 0 ? key.substring(0, slash) : "";
    }

    protected boolean hasSectionRoot()
    {
        return false;
    }

    protected FormCategory getCategory(String key)
    {
        if (!BBSSettings.morphingFolderHierarchy.get())
        {
            String newKey = this.getKey(key);

            return this.categories.computeIfAbsent(newKey, (k) ->
            {
                IKey uiKey = this.getTitle();

                if (!newKey.isEmpty())
                {
                    uiKey = IKey.comp(Arrays.asList(uiKey, IKey.constant(" (" + newKey + ")")));
                }

                return this.createCategory(uiKey, key);
            });
        }

        String folderPath = this.getKey(key);

        if (folderPath.isEmpty())
        {
            return this.categories.computeIfAbsent("", (k) ->
            {
                return this.createCategory(this.getTitle(), "");
            });
        }

        String[] parts = folderPath.split("/");
        String currentPath = "";
        FormCategory parentCat = null;

        if (this.hasSectionRoot())
        {
            parentCat = this.categories.computeIfAbsent("", (k) ->
            {
                return this.createCategory(this.getTitle(), "");
            });
        }

        FormCategory targetCat = null;

        for (int i = 0; i < parts.length; i++)
        {
            currentPath = currentPath.isEmpty() ? parts[i] : currentPath + "/" + parts[i];
            boolean isRoot = (i == 0 && parentCat == null);
            final String finalPath = currentPath;
            final boolean finalIsRoot = isRoot;
            final FormCategory finalParent = parentCat;

            FormCategory cat = this.categories.computeIfAbsent(currentPath, (k) ->
            {
                IKey uiKey = this.getCategoryTitle(finalPath, finalIsRoot);
                FormCategory created = this.createCategory(uiKey, finalPath);

                if (finalParent != null)
                {
                    created.setParent(finalParent);
                }

                return created;
            });

            if (parentCat != null && cat.getParent() == null)
            {
                cat.setParent(parentCat);
            }

            parentCat = cat;
            targetCat = cat;
        }

        return targetCat;
    }

    protected void add(String key)
    {
        FormCategory category = this.getCategory(key);

        for (Form form : category.getForms())
        {
            if (this.isEqual(form, key))
            {
                return;
            }
        }

        category.addForm(this.create(key));
    }

    protected void remove(String key)
    {
        FormCategory category = this.getCategory(key);
        Iterator<Form> it = category.getDirectForms().iterator();

        while (it.hasNext())
        {
            if (this.isEqual(it.next(), key))
            {
                it.remove();
                this.parent.markDirty();
            }
        }

        if (category.getForms().isEmpty() && category.getChildren().isEmpty())
        {
            this.categories.remove(this.getKey(key));

            if (category.getParent() != null)
            {
                category.setParent(null);
            }

            this.parent.markDirty();
        }
    }

    @Override
    public List<FormCategory> getCategories()
    {
        if (!BBSSettings.morphingFolderHierarchy.get())
        {
            return new ArrayList<>(this.categories.values());
        }

        List<FormCategory> result = new ArrayList<>();
        FormCategory root = this.categories.get("");

        if (root != null && (!root.getForms().isEmpty() || !root.getChildren().isEmpty()))
        {
            this.addCategoryTree(root, result);
        }

        for (Map.Entry<String, FormCategory> entry : this.categories.entrySet())
        {
            if (entry.getKey().isEmpty())
            {
                continue;
            }

            FormCategory category = entry.getValue();

            if (category.getParent() == null)
            {
                this.addCategoryTree(category, result);
            }
        }

        return result;
    }

    private void addCategoryTree(FormCategory category, List<FormCategory> result)
    {
        result.add(category);

        for (FormCategory child : category.getChildren())
        {
            this.addCategoryTree(child, result);
        }
    }
}