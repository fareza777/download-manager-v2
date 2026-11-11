"""Render an F7 icon concept in the running Blender session (v3).

The first two attempts were occluded by the user's default Cube at the origin. This version
temporarily hides every object it did not create, renders, then restores hide_render and
removes its own objects in a finally block, so the session is left as found.
"""

import math
import os

import bpy
from mathutils import Vector

OUT = r"C:\Download Manager V2 ember\design\icon-concept.png"
FONT = r"C:\Download Manager V2 ember\app\src\main\res\font\plus_jakarta_sans_extrabold.ttf"
PREFIX = "F7X_"

os.makedirs(os.path.dirname(OUT), exist_ok=True)
scene = bpy.context.scene
report = []
hidden = {}


def set_input(node, names, value):
    for name in names:
        if name in node.inputs:
            node.inputs[name].default_value = value
            return True
    return False


def material(name, base, metallic, roughness, emission=None, strength=0.0):
    mat = bpy.data.materials.new(PREFIX + name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    set_input(bsdf, ["Base Color"], (*base, 1.0))
    set_input(bsdf, ["Metallic"], metallic)
    set_input(bsdf, ["Roughness"], roughness)
    set_input(bsdf, ["Coat Weight", "Clearcoat"], 0.45)
    set_input(bsdf, ["Coat Roughness", "Clearcoat Roughness"], 0.14)
    if emission is not None:
        set_input(bsdf, ["Emission Color", "Emission"], (*emission, 1.0))
        set_input(bsdf, ["Emission Strength"], strength)
    return mat


def aim(obj, target):
    obj.rotation_euler = (Vector(target) - obj.location).to_track_quat("-Z", "Y").to_euler()


def cleanup():
    for obj in list(bpy.data.objects):
        if obj.name.startswith(PREFIX):
            bpy.data.objects.remove(obj, do_unlink=True)
    for name, value in hidden.items():
        obj = bpy.data.objects.get(name)
        if obj is not None:
            obj.hide_render = value


for obj in list(bpy.data.objects):
    if obj.name.startswith(PREFIX):
        bpy.data.objects.remove(obj, do_unlink=True)

try:
    for obj in list(bpy.data.objects):
        if not obj.name.startswith(PREFIX):
            hidden[obj.name] = obj.hide_render
            obj.hide_render = True

    # --- slab ---------------------------------------------------------------
    bpy.ops.mesh.primitive_cube_add(size=1.0, location=(0.0, 0.0, 0.0))
    slab = bpy.context.active_object
    slab.name = PREFIX + "Slab"
    slab.scale = (2.4, 0.34, 2.4)
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    bevel = slab.modifiers.new(PREFIX + "Bevel", "BEVEL")
    bevel.width = 0.20
    bevel.segments = 10
    bevel.limit_method = "ANGLE"
    slab.data.materials.append(material("Slab", (0.045, 0.060, 0.055), 0.7, 0.14, (0.02, 0.10, 0.08), 0.2))
    bpy.ops.object.shade_auto_smooth(angle=math.radians(40))
    report.append("slab=%s" % (tuple(round(v, 3) for v in slab.dimensions),))

    # --- wordmark -----------------------------------------------------------
    curve = bpy.data.curves.new(PREFIX + "Word", type="FONT")
    curve.body = "F7"
    curve.align_x = "CENTER"
    curve.align_y = "CENTER"
    curve.size = 1.0
    curve.extrude = 0.045
    curve.bevel_depth = 0.012
    curve.bevel_resolution = 3
    if os.path.exists(FONT):
        curve.font = bpy.data.fonts.load(FONT)
        report.append("font=" + curve.font.name)
    word = bpy.data.objects.new(PREFIX + "Word", curve)
    scene.collection.objects.link(word)
    word.location = (0.0, -0.19, 0.0)
    word.rotation_euler = (math.radians(90), 0.0, 0.0)
    bpy.context.view_layer.objects.active = word
    word.select_set(True)
    bpy.ops.object.convert(target="MESH")
    word = bpy.context.active_object
    if word.dimensions.x > 0.01:
        factor = 1.6 / word.dimensions.x
        word.scale = (factor, factor, factor)
        bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    word.location = (0.0, -0.19, 0.0)
    word.data.materials.append(material("Word", (0.62, 0.99, 0.66), 0.2, 0.22, (0.40, 1.0, 0.58), 0.6))
    report.append("word=%s verts=%d" % (tuple(round(v, 3) for v in word.dimensions), len(word.data.vertices)))

    # --- floor --------------------------------------------------------------
    bpy.ops.mesh.primitive_plane_add(size=40.0, location=(0.0, 0.0, -1.32))
    floor = bpy.context.active_object
    floor.name = PREFIX + "Floor"
    floor.data.materials.append(material("Floor", (0.965, 0.945, 0.910), 0.0, 0.92))

    # --- lights -------------------------------------------------------------
    def area(name, location, energy, size, color):
        data = bpy.data.lights.new(PREFIX + name, type="AREA")
        data.energy = energy
        data.size = size
        data.color = color
        obj = bpy.data.objects.new(PREFIX + name, data)
        scene.collection.objects.link(obj)
        obj.location = location
        aim(obj, (0, 0, 0))

    area("Key", (-3.6, -4.8, 3.9), 900, 5.0, (1.0, 0.97, 0.92))
    area("Fill", (4.0, -3.6, 0.4), 260, 6.0, (0.86, 0.93, 1.0))
    area("Rim", (2.2, 3.4, 2.6), 800, 3.5, (0.42, 1.0, 0.78))

    # --- camera -------------------------------------------------------------
    cam_data = bpy.data.cameras.new(PREFIX + "Camera")
    cam_data.lens = 85
    cam = bpy.data.objects.new(PREFIX + "Camera", cam_data)
    scene.collection.objects.link(cam)
    cam.location = (0.0, -10.4, 1.15)
    aim(cam, (0.0, 0.0, -0.02))
    scene.camera = cam

    # --- world --------------------------------------------------------------
    world = bpy.data.worlds.get(PREFIX + "World") or bpy.data.worlds.new(PREFIX + "World")
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs["Color"].default_value = (0.945, 0.925, 0.885, 1.0)
        bg.inputs["Strength"].default_value = 0.5
    scene.world = world

    # --- render -------------------------------------------------------------
    engines = [item.identifier for item in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items]
    scene.render.engine = (
        "BLENDER_EEVEE_NEXT" if "BLENDER_EEVEE_NEXT" in engines
        else "BLENDER_EEVEE" if "BLENDER_EEVEE" in engines
        else "CYCLES"
    )
    if scene.render.engine == "CYCLES":
        scene.cycles.samples = 48
        scene.cycles.use_denoising = True
    else:
        try:
            scene.eevee.taa_render_samples = 96
        except Exception:
            pass

    scene.render.resolution_x = 900
    scene.render.resolution_y = 900
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = False
    scene.render.image_settings.file_format = "PNG"
    scene.render.image_settings.color_mode = "RGBA"
    scene.render.filepath = OUT
    try:
        scene.view_settings.view_transform = "AgX"
        scene.view_settings.look = "AgX - Medium High Contrast"
    except Exception:
        pass

    bpy.ops.render.render(write_still=True)
    report.append("engine=" + scene.render.engine)
    report.append("size=%d" % (os.path.getsize(OUT) if os.path.exists(OUT) else -1))
finally:
    cleanup()

print("RENDER_OK")
print("\n".join(report))
