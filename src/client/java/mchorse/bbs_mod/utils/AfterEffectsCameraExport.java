package mchorse.bbs_mod.utils;

import com.google.gson.Gson;
import org.joml.Matrix4fc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Streams camera metadata for frames actually sent to ffmpeg, not render callbacks. */
public final class AfterEffectsCameraExport implements AutoCloseable
{
    public record Sample(double x, double y, double z, double yaw, double pitch, double roll, double zoom)
    {
        public Sample
        {
            for (double value : new double[] {x, y, z, yaw, pitch, roll, zoom})
            {
                if (!Double.isFinite(value)) throw new IllegalArgumentException("Invalid camera sample");
            }
            if (zoom <= 0) throw new IllegalArgumentException("Invalid camera zoom");
        }

        public static Sample fromMinecraft(double x, double y, double z, double yaw, double pitch,
            double roll, Matrix4fc projection, int height)
        {
            if (Math.abs(projection.m23() + 1F) > 0.001F || Math.abs(projection.m33()) > 0.001F)
                throw new IllegalArgumentException("After Effects camera export requires a perspective camera");

            /* Minecraft puts roll in the projection. The vertical row's length recovers
             * the focal scale without multiplying it by cos(roll). */
            double scale = Math.sqrt((double) projection.m01() * projection.m01()
                + (double) projection.m11() * projection.m11() + (double) projection.m21() * projection.m21());
            return new Sample(x, y, z, yaw + 180D, -pitch, roll, height * 0.5D * scale);
        }
    }

    private final Path target;
    private final Path temporary;
    private final BufferedWriter writer;
    private final int samplesPerFrame;
    private int samples;
    private int frames;
    private Sample midpoint;
    private double originX;
    private double originY;
    private double originZ;
    private boolean closed;

    public AfterEffectsCameraExport(Path target, String name, int width, int height, double fps, int motionBlur) throws IOException
    {
        if (width < 2 || height < 2 || fps < 1 || fps > 99 || motionBlur < 0 || motionBlur > 20)
            throw new IllegalArgumentException("After Effects export supports perspective video at 1-99 FPS");

        this.target = target;
        this.samplesPerFrame = 1 << motionBlur;
        Files.createDirectories(target.toAbsolutePath().getParent());
        this.temporary = Files.createTempFile(target.toAbsolutePath().getParent(), "bbs-camera-", ".jsx.partial");
        this.writer = Files.newBufferedWriter(this.temporary, StandardCharsets.UTF_8);
        try
        {
            this.writer.write("// BBS camera export. Run with File > Scripts > Run Script File.\n");
            this.writer.write("// One Minecraft block = SCALE AE units. Y and Z are inverted.\n");
            this.writer.write("// Camera rig order: world Y yaw, local X pitch, local Z roll. No world geometry.\n");
            this.writer.write("// Motion blur samples use the temporal midpoint of each complete blend group.\n");
            this.writer.write("(function () {\nvar NAME=" + new Gson().toJson(name) + ";\n");
            this.writer.write("var WIDTH=" + width + ", HEIGHT=" + height + ", FPS=" + fps + ", SCALE=100;\n");
            this.writer.write("var frames=[\n");
        }
        catch (IOException failure)
        {
            try { this.abort(); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public int getFrameCount()
    {
        return this.frames;
    }

    public void add(Sample sample) throws IOException
    {
        if (this.closed) throw new IllegalStateException("Camera export is closed");
        int middle = this.samplesPerFrame / 2;
        if (this.samplesPerFrame == 1) this.midpoint = sample;
        else if (this.samples == middle - 1) this.midpoint = sample;
        else if (this.samples == middle) this.midpoint = midpoint(this.midpoint, sample);

        this.samples++;
        if (this.samples != this.samplesPerFrame) return;
        this.samples = 0;
        Sample value = this.midpoint;
        if (this.frames == 0)
        {
            this.originX = Math.floor(value.x());
            this.originY = Math.floor(value.y());
            this.originZ = Math.floor(value.z());
        }
        if (this.frames > 0) this.writer.write(",\n");
        this.writer.write("[" + (value.x() - this.originX) + "," + (value.y() - this.originY)
            + "," + (value.z() - this.originZ) + "," + value.yaw() + "," + value.pitch()
            + "," + value.roll() + "," + value.zoom() + "]");
        this.frames++;
    }

    private static Sample midpoint(Sample a, Sample b)
    {
        return new Sample(a.x() + (b.x() - a.x()) * 0.5D, a.y() + (b.y() - a.y()) * 0.5D,
            a.z() + (b.z() - a.z()) * 0.5D, angleMidpoint(a.yaw(), b.yaw()),
            angleMidpoint(a.pitch(), b.pitch()), angleMidpoint(a.roll(), b.roll()), (a.zoom() + b.zoom()) * 0.5D);
    }

    private static double angleMidpoint(double a, double b)
    {
        double delta = (b - a) % 360D;
        if (delta > 180D) delta -= 360D;
        if (delta < -180D) delta += 360D;
        return a + delta * 0.5D;
    }

    public void abort() throws IOException
    {
        if (this.closed) return;
        this.closed = true;
        try { this.writer.close(); }
        finally { Files.deleteIfExists(this.temporary); }
    }

    @Override
    public void close() throws IOException
    {
        if (this.closed) return;
        if (this.frames == 0)
        {
            this.abort();
            return;
        }
        try
        {
            this.writer.write("\n];\nvar ORIGIN=[" + this.originX + "," + this.originY + "," + this.originZ + "];\n");
            this.writer.write(SCRIPT);
            this.writer.close();
            Files.move(this.temporary, this.target, StandardCopyOption.REPLACE_EXISTING);
            this.closed = true;
        }
        catch (IOException failure)
        {
            this.abort();
            throw failure;
        }
    }

    private static final String SCRIPT = """
        // Place a Minecraft point using mcToAE(x,y,z), or the formula in the origin null's comment.
        function mcToAE(x,y,z) { return [centerX+(x-ORIGIN[0])*SCALE, centerY-(y-ORIGIN[1])*SCALE, -(z-ORIGIN[2])*SCALE]; }
        function transform(layer) { return layer.property("ADBE Transform Group"); }
        function reset(layer) {
            var t=transform(layer);
            t.property("ADBE Position").setValue([0,0,0]);
            t.property("ADBE Orientation").setValue([0,0,0]);
            t.property("ADBE Rotate X").setValue(0);
            t.property("ADBE Rotate Y").setValue(0);
            t.property("ADBE Rotate Z").setValue(0);
        }
        function null3D(comp,name,parent) {
            var n=comp.layers.addNull(comp.duration); n.name=name; n.threeDLayer=true;
            if (parent) n.parent=parent;
            transform(n).property("ADBE Anchor Point").setValue([0,0,0]);
            reset(n); n.guideLayer=true; return n;
        }
        function keys(property,times,values) {
            property.setValuesAtTimes(times,values);
            for (var i=1;i<=property.numKeys;i++)
                property.setInterpolationTypeAtKey(i,KeyframeInterpolationType.HOLD,KeyframeInterpolationType.HOLD);
        }
        app.beginUndoGroup("Import BBS camera");
        try {
            if (!app.project) app.newProject();
            var comp=app.project.activeItem;
            if (!(comp instanceof CompItem))
                comp=app.project.items.addComp(NAME+" - BBS camera",WIDTH,HEIGHT,1,frames.length/FPS,FPS);
            var centerX=comp.width/2, centerY=comp.height/2;
            var origin=null3D(comp,"BBS world origin",null);
            transform(origin).property("ADBE Position").setValue([centerX,centerY,0]);
            origin.comment="Minecraft origin: ["+ORIGIN.join(", ")+"]. One block = "+SCALE+" units. AE position = ["+
                centerX+"+(x-"+ORIGIN[0]+")*"+SCALE+", "+centerY+"-(y-"+ORIGIN[1]+")*"+SCALE+", -(z-"+ORIGIN[2]+")*"+SCALE+"].";
            var yaw=null3D(comp,"BBS camera - position and yaw",null);
            var pitch=null3D(comp,"BBS camera - pitch",yaw);
            var camera=comp.layers.addCamera("BBS Camera",[centerX,centerY]);
            camera.autoOrient=AutoOrientType.NO_AUTO_ORIENT;
            camera.parent=pitch; reset(camera);
            camera.property("ADBE Camera Options Group").property("ADBE Camera Depth of Field").setValue(0);
            var times=[], positions=[], yaws=[], pitches=[], rolls=[], zooms=[];
            for (var i=0;i<frames.length;i++) {
                var f=frames[i]; times.push(i/FPS);
                positions.push([centerX+f[0]*SCALE,centerY-f[1]*SCALE,-f[2]*SCALE]);
                yaws.push(f[3]); pitches.push(f[4]); rolls.push(f[5]); zooms.push(f[6]);
            }
            keys(transform(yaw).property("ADBE Position"),times,positions);
            keys(transform(yaw).property("ADBE Rotate Y"),times,yaws);
            keys(transform(pitch).property("ADBE Rotate X"),times,pitches);
            keys(transform(camera).property("ADBE Rotate Z"),times,rolls);
            keys(camera.property("ADBE Camera Options Group").property("ADBE Camera Zoom"),times,zooms);
            comp.openInViewer();
        } catch (error) { alert("BBS camera import failed: "+error.toString()); }
        finally { app.endUndoGroup(); }
        }());
        """;
}
