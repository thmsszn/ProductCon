package com.crudecompute.client;

import com.crudecompute.CrudeCompute;
import java.util.List;
import net.minecraft.client.resources.model.ModelResourceLocation;

/**
 * Stand-alone OBJ models rendered by block entity renderers, plus the pivot points they rotate around.
 * The numbers mirror tools/models.py (model space: block units, facing north, controller cell at the origin).
 */
public final class ModModels {
    public static final ModelResourceLocation SHAFT = model("shaft");
    public static final ModelResourceLocation SHAFT_HALF = model("shaft_half");
    public static final ModelResourceLocation HAND_CRANK_HANDLE = model("hand_crank_handle");
    public static final ModelResourceLocation WINDMILL_ROTOR = model("windmill_rotor");
    public static final ModelResourceLocation ENGINE_FLYWHEEL = model("diesel_engine_flywheel");
    public static final ModelResourceLocation PUMPJACK_BEAM = model("pumpjack_beam");
    public static final ModelResourceLocation PUMPJACK_CRANK = model("pumpjack_crank");
    public static final ModelResourceLocation PUMPJACK_PITMAN = model("pumpjack_pitman");
    public static final ModelResourceLocation PUMPJACK_CABLE = model("pumpjack_cable");
    public static final ModelResourceLocation PUMPJACK_ROD = model("pumpjack_rod");
    public static final ModelResourceLocation GPU_BLADE = model("gpu_blade");
    public static final ModelResourceLocation COOLING_FAN = model("cooling_unit_fan");
    public static final ModelResourceLocation CONVEYOR_RIB = model("conveyor_rib");
    public static final ModelResourceLocation DERRICK_BLOCK = model("derrick_block");
    public static final ModelResourceLocation DERRICK_KELLY = model("derrick_kelly");
    public static final ModelResourceLocation RACK_FAN_FRAME = model("rack_fan_frame");
    public static final ModelResourceLocation RACK_FAN_FRAME_HP = model("rack_fan_frame_hp");
    public static final ModelResourceLocation RACK_FAN_ROTOR = model("rack_fan_rotor");
    public static final ModelResourceLocation RACK_FAN_ROTOR_HP = model("rack_fan_rotor_hp");
    public static final ModelResourceLocation SERVER_1U = model("server_1u");
    public static final ModelResourceLocation SERVER_2U = model("server_2u");
    public static final ModelResourceLocation SERVER_4U = model("server_4u");
    public static final ModelResourceLocation PUMP_GEAR = model("mechanical_pump_gear");
    public static final ModelResourceLocation CHIP_FAB_BRIDGE = model("chip_fab_bridge");
    public static final ModelResourceLocation CHIP_FAB_HEAD = model("chip_fab_head");
    public static final ModelResourceLocation CHIP_FAB_WAFER = model("chip_fab_wafer");

    public static final List<ModelResourceLocation> ALL = List.of(SHAFT, SHAFT_HALF, HAND_CRANK_HANDLE, WINDMILL_ROTOR,
            ENGINE_FLYWHEEL, PUMPJACK_BEAM, PUMPJACK_CRANK, PUMPJACK_PITMAN, PUMPJACK_CABLE, PUMPJACK_ROD, GPU_BLADE, COOLING_FAN,
            RACK_FAN_FRAME, RACK_FAN_FRAME_HP, RACK_FAN_ROTOR, RACK_FAN_ROTOR_HP, SERVER_1U, SERVER_2U, SERVER_4U, CONVEYOR_RIB, DERRICK_BLOCK, DERRICK_KELLY, PUMP_GEAR, CHIP_FAB_BRIDGE, CHIP_FAB_HEAD, CHIP_FAB_WAFER);

    // pumpjack geometry
    public static final float PJ_PIVOT_Y = 2.4f;
    public static final float PJ_PIVOT_Z = -0.3f;
    public static final float PJ_CRANK_Y = 0.5f;
    public static final float PJ_CRANK_Z = 0.5f;
    public static final float PJ_CRANK_R = 0.25f;
    public static final float PJ_BEAM_BACK = 0.8f;
    public static final float PJ_HEAD_R = 1.2f;
    public static final float PJ_CARRIER_Y = 1.56f;
    public static final float PJ_EQUALIZER_DROP = 0.16f;
    public static final float PJ_GEAR_RATIO = 0.25f;

    // windmill hub
    public static final float WINDMILL_HUB_Y = 0.72f;
    public static final float WINDMILL_HUB_Z = 0.26f;

    // server rack bays
    public static final float RACK_BAY0 = 0.12f;
    public static final float RACK_BAY_H = 0.21f;

    private ModModels() {
    }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(CrudeCompute.id("block/" + name));
    }
}
