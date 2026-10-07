package mchorse.bbs_mod.cubic.model.loaders;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mchorse.bbs_mod.cubic.CubicLoader;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.animation.Animation;
import mchorse.bbs_mod.cubic.data.animation.Animations;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelData;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.data.model.ModelMesh;
import mchorse.bbs_mod.cubic.geo.GeoAnimationParser;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.obj.MeshOBJ;
import mchorse.bbs_mod.obj.MeshesOBJ;
import mchorse.bbs_mod.obj.OBJMaterial;
import mchorse.bbs_mod.obj.OBJParser;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.CollectionUtils;
import mchorse.bbs_mod.utils.IOUtils;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.resources.LinkUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CubicModelLoader implements IModelLoader
{
    @Override
    public ModelInstance load(String id, ModelManager models, Link model, Collection<Link> links, MapType config)
    {
        Link modelBBS = IModelLoader.getLink(model.combine("model.bbs.json"), links, ".bbs.json");
        Link modelTexture = IModelLoader.getLink(model.combine("model.png"), links, ".png");
        ModelInstance newModel = new ModelInstance(id, null, new Animations(models.parser), modelTexture);
        Map<String, MeshesOBJ> compile = this.tryLoadingOBJMeshes(models, model, IModelLoader.getLinks(links, ".obj"));
        Model theModel = null;

        try (InputStream stream = models.provider.getAsset(modelBBS))
        {
            CubicLoader loader = new CubicLoader();
            CubicLoader.LoadingInfo info = loader.load(models.parser, stream, modelBBS.path);

            if (info.model != null)
            {
                theModel = info.model;
            }

            if (info.animations != null)
            {
                for (Animation animation : info.animations.getAll())
                {
                    newModel.animations.add(animation);
                }
            }
        }
        catch (Exception e)
        {
            System.err.println("Failed to load BBS file: " + modelBBS);
        }

        /* Construct the model from compiled data */
        if (!compile.isEmpty())
        {
            HashSet<String> declined = new HashSet<>();

            if (theModel == null)
            {
                theModel = new Model(models.parser);
                theModel.textureWidth = 1;
                theModel.textureHeight = 1;
            }

            this.loadMaterialTextures(models.provider, links, model, newModel, theModel, compile);

            for (Map.Entry<String, MeshesOBJ> entry : compile.entrySet())
            {
                MeshesOBJ value = entry.getValue();
                ModelGroup group = theModel.getGroup(entry.getKey());

                if (group == null)
                {
                    group = new ModelGroup(entry.getKey());

                    theModel.topGroups.add(group);
                }

                for (MeshOBJ mesh : value.meshes)
                {
                    ModelMesh modelMesh = new ModelMesh();

                    modelMesh.material = mesh.material == null ? "" : mesh.material.name;
                    modelMesh.baseData.fill(mesh, theModel.textureWidth, theModel.textureHeight);
                    group.meshes.add(modelMesh);
                }

                this.fillShapes(declined, value.shapes, group, theModel.textureWidth, theModel.textureHeight);
            }

            theModel.initialize();

            for (String s : declined)
            {
                System.out.println("Model \"" + model + "\" has shape keys \"" + s + "\" that have invalid shape keys (triangle count doesn't match)!");
            }
        }

        if (theModel == null || theModel.topGroups.isEmpty())
        {
            return null;
        }

        newModel.model = theModel;

        /* The model editor writes back only what it fully owns: a cubic model that is a real file in
         * the user's assets (a pack's model resolves to a path that isn't there), with nothing compiled
         * in from OBJ meshes — serialising those would bake them into the .bbs.json. */
        if (compile.isEmpty())
        {
            File file = models.provider.getFile(modelBBS);

            if (file != null && file.isFile())
            {
                newModel.setModelFile(modelBBS);
            }
        }

        this.tryLoadingFolderAnimations(models, links, modelBBS, newModel.animations);
        this.tryLoadingConfigAnimations(models, config, newModel.animations);

        newModel.applyConfig(config);

        return newModel;
    }

    /**
     * Load each material's default texture into the model instance. Textures live in a folder
     * named after the material, either {@code <model>/<material>/} or {@code <model>/textures/<material>/}
     * (first PNG wins); a material with no such file gets a 1x1 swatch of its flat diffuse (Kd)
     * color. OBJ UVs stay normalized (0..1) into each material's own texture, so the model's
     * texture sheet is kept at 1x1.
     */
    private void loadMaterialTextures(AssetProvider provider, Collection<Link> links, Link model, ModelInstance newModel, Model theModel, Map<String, MeshesOBJ> compile)
    {
        List<OBJMaterial> materials = new ArrayList<>();

        for (MeshesOBJ value : compile.values())
        {
            for (MeshOBJ mesh : value.meshes)
            {
                if (mesh.material != null && !materials.contains(mesh.material))
                {
                    materials.add(mesh.material);
                }
            }
        }

        if (materials.isEmpty())
        {
            return;
        }

        theModel.textureWidth = 1;
        theModel.textureHeight = 1;

        for (OBJMaterial material : materials)
        {
            newModel.materials.add(material.name);

            Link texture = IModelLoader.findMaterialTexture(links, model, material.name);

            if (texture == null)
            {
                /* No texture yet: surface an empty folder for it and render the flat Kd color meanwhile. */
                IModelLoader.ensureMaterialFolder(provider, model, material.name);
                texture = LinkUtils.color(material.r, material.g, material.b);
            }

            newModel.materialTextures.put(material.name, texture);
        }
    }

    /**
     * Load OBJ meshes from multiple files
     */
    private Map<String, MeshesOBJ> tryLoadingOBJMeshes(ModelManager models, Link model, List<Link> modelsOBJ)
    {
        Map<String, MeshesOBJ> compile = new HashMap<>();

        /* Load the base OBJ file */
        for (Link link : modelsOBJ)
        {
            String path = link.path.substring(model.path.length() + 1);

            if (path.contains("/"))
            {
                continue;
            }

            Link mtl = new Link(link.source, StringUtils.removeExtension(link.path) + ".mtl");

            try (InputStream stream = models.provider.getAsset(link))
            {
                InputStream mtlStream = null;

                try
                {
                    mtlStream = models.provider.getAsset(mtl);
                }
                catch (Exception e)
                {}

                OBJParser parser = new OBJParser(stream, mtlStream);

                parser.read();
                compile.putAll(parser.compile());

                if (mtlStream != null)
                {
                    mtlStream.close();
                }

                break;
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        /* Load shapes from shapes folder */
        for (Link link : modelsOBJ)
        {
            if (!link.path.contains("/shapes/"))
            {
                continue;
            }

            try (InputStream stream = models.provider.getAsset(link))
            {
                OBJParser parser = new OBJParser(stream, null);
                String name = StringUtils.fileName(StringUtils.removeExtension(link.path));

                parser.read();

                Map<String, MeshesOBJ> compiled = parser.compile();

                for (Map.Entry<String, MeshesOBJ> entry : compiled.entrySet())
                {
                    MeshesOBJ meshesOBJ = compile.get(entry.getKey());

                    if (meshesOBJ == null)
                    {
                        compile.put(entry.getKey(), entry.getValue());
                    }
                    else
                    {
                        meshesOBJ.mergeShape(name, entry.getValue());
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return compile;
    }

    private void fillShapes(Set<String> declined, Map<String, List<MeshOBJ>> shapes, ModelGroup group, int tw, int th)
    {
        if (shapes == null)
        {
            return;
        }

        for (Map.Entry<String, List<MeshOBJ>> shapeEntry : shapes.entrySet())
        {
            int i = 0;

            for (MeshOBJ mesh : shapeEntry.getValue())
            {
                ModelMesh modelMesh = CollectionUtils.getSafe(group.meshes, i);
                ModelData data = new ModelData();

                data.fill(mesh, tw, th);

                if (
                    data.vertices.size() == modelMesh.baseData.vertices.size() &&
                    data.normals.size() == modelMesh.baseData.normals.size() &&
                    data.uvs.size() == modelMesh.baseData.uvs.size()
                ) {
                    modelMesh.data.put(shapeEntry.getKey(), data);
                }
                else
                {
                    declined.add(shapeEntry.getKey());
                }

                i += 1;
            }
        }
    }

    /**
     * Auto-detect and load external animation files located in the model folder
     */
    private void tryLoadingFolderAnimations(ModelManager models, Collection<Link> links, Link modelBBS, Animations target)
    {
        for (Link link : links)
        {
            if (link.equals(modelBBS))
            {
                continue;
            }

            if (link.path.endsWith("/config.json") || link.path.equals("config.json") || link.path.endsWith(".geo.json"))
            {
                continue;
            }

            if (link.path.endsWith(".png") || link.path.endsWith(".obj") || link.path.endsWith(".mtl")
                || link.path.endsWith(".bobj") || link.path.endsWith(".jem") || link.path.endsWith(".jpm")
                || link.path.endsWith(".vox"))
            {
                continue;
            }

            if (link.path.contains("/shapes/") || link.path.contains("/materials/"))
            {
                continue;
            }

            if (link.path.endsWith(".animation.json") || link.path.endsWith(".bbs.json")
                || link.path.contains("/animations/") || link.path.endsWith(".json"))
            {
                this.loadAnimationsFromFile(models, link, target);
            }
        }
    }

    /**
     * Loading external animations mentioned in the config
     */
    private void tryLoadingConfigAnimations(ModelManager models, MapType config, Animations target)
    {
        if (config == null)
        {
            return;
        }

        for (BaseType type : config.getList("animations"))
        {
            if (type.isString())
            {
                Link animationFile = Link.create(type.asString());

                this.loadAnimationsFromFile(models, animationFile, target);
            }
        }
    }

    /**
     * Load animations from a specific file (supports Bedrock and BBS animation formats)
     */
    private void loadAnimationsFromFile(ModelManager models, Link link, Animations target)
    {
        try (InputStream stream = models.provider.getAsset(link))
        {
            if (stream == null)
            {
                return;
            }

            String content = IOUtils.readText(stream);
            JsonElement rootElement;

            try
            {
                rootElement = JsonParser.parseString(content);
            }
            catch (Exception e)
            {
                return;
            }

            if (!rootElement.isJsonObject())
            {
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            boolean isBedrock = link.path.endsWith(".animation.json") || root.has("format_version");

            String baseFileName = StringUtils.fileName(StringUtils.removeExtension(link.path));

            if (baseFileName.endsWith(".animation"))
            {
                baseFileName = StringUtils.removeExtension(baseFileName);
            }

            if (root.has("animations") && root.get("animations").isJsonObject())
            {
                JsonObject animationsObj = root.getAsJsonObject("animations");

                for (String key : animationsObj.keySet())
                {
                    JsonElement animElement = animationsObj.get(key);

                    if (!animElement.isJsonObject())
                    {
                        continue;
                    }

                    JsonObject animObj = animElement.getAsJsonObject();

                    if (isBedrock || animObj.has("bones") || animObj.has("animation_length"))
                    {
                        try
                        {
                            Animation animation = GeoAnimationParser.parse(models.parser, key, animObj);

                            target.add(animation);

                            if (animationsObj.size() == 1 && !baseFileName.isEmpty())
                            {
                                target.addAlias(baseFileName, animation);
                            }
                        }
                        catch (Exception e)
                        {
                            System.err.println("Failed to parse Bedrock animation: " + key + " in " + link);
                        }
                    }
                    else if (animObj.has("groups") || animObj.has("length") || animObj.has("duration"))
                    {
                        try
                        {
                            MapType map = (MapType) DataToString.fromString(animObj.toString());
                            Animation animation = new Animation(key, models.parser);

                            animation.fromData(map);
                            target.add(animation);

                            if (animationsObj.size() == 1 && !baseFileName.isEmpty())
                            {
                                target.addAlias(baseFileName, animation);
                            }
                        }
                        catch (Exception e)
                        {
                            System.err.println("Failed to parse BBS animation: " + key + " in " + link);
                        }
                    }
                }
            }
            else if (root.has("groups") || root.has("length") || root.has("duration"))
            {
                try
                {
                    MapType map = (MapType) DataToString.fromString(root.toString());
                    Animation animation = new Animation(baseFileName, models.parser);

                    animation.fromData(map);
                    target.add(animation);
                }
                catch (Exception e)
                {
                    System.err.println("Failed to parse BBS animation from " + link);
                }
            }
            else if (root.has("bones") || root.has("animation_length"))
            {
                try
                {
                    Animation animation = GeoAnimationParser.parse(models.parser, baseFileName, root);

                    target.add(animation);
                }
                catch (Exception e)
                {
                    System.err.println("Failed to parse Bedrock animation from " + link);
                }
            }
        }
        catch (FileNotFoundException e)
        {
            /* Ignore missing files */
        }
        catch (Exception e)
        {
            System.err.println("Failed to load animation file: " + link);
        }
    }
}