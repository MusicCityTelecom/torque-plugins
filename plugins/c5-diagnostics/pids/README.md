# Torque extended PID pack

gm_c5_ls1_p59.csv can be imported into Torque Pro as an extended PID set for independent testing of the enhanced PCM counters.

The Android plugin does not depend on this CSV. It queries and parses its required enhanced data directly through Torque's remote API, which is necessary for the combined EBCM wheel packet.

The CSV defaults to the P59-corrected cylinder 1/2 current mapping. If your scan tool shows those two cylinders reversed, use the app setting or edit those two rows before importing.
