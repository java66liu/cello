package org.cellocad.adaptors.eugeneadaptor;

import lombok.Getter;
import lombok.Setter;
import org.apache.log4j.Logger;
import org.cellocad.MIT.dnacompiler.*;
import org.cidarlab.eugene.Eugene;
import org.cidarlab.eugene.dom.Device;
import org.cidarlab.eugene.dom.NamedElement;
import org.cidarlab.eugene.dom.imp.container.EugeneArray;
import org.cidarlab.eugene.dom.imp.container.EugeneCollection;
import org.cidarlab.eugene.exception.EugeneException;
import org.cidarlab.eugene.util.DeviceUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

public class EugeneAdaptor {



	/**
	 * Execute Eugene
	 *
	 * @param name_Eug_file
	 *            .eug file path
	 * @param module_variants
	 *            passed as empty ArrayList, populated within the method
	 * @param part_library
	 *            Part objects added to module_variants based on part name from
	 *            Eugene design
	 * @param options
	 *            number of variants to design (nP) and output directory
	 */
	public void callEugene(String name_Eug_file,
			ArrayList<ArrayList<Part>> module_variants,
			PartLibrary part_library, Args options) {

		try {
			Eugene e = new Eugene();

			// 'exports' directory is created but empty. Put it here:
			// Eugene.ROOT_DIRECTORY = options.home + "/resources/eugene/";

			EugeneCollection ec = e.executeFile(new File(options
					.get_output_directory() + name_Eug_file));

			EugeneArray variants = (EugeneArray) ec.get("allResults");

			int n_variants = options.get_nP();

			if (variants.getElements().size() < options.get_nP()) {
				n_variants = variants.getElements().size();
			}

			for (int i = 0; i < n_variants; ++i) {

				NamedElement circuit = variants.getElement(i);

				if (circuit instanceof org.cidarlab.eugene.dom.Device) {

					/*
					 * The full DNA sequence of the circuit is generated by
					 * Eugene:
					 */

					ArrayList<Part> module = new ArrayList<Part>();

					int g_index = 0;

					for (NamedElement gate : ((Device) circuit)
							.getComponentList()) {

						if (gate instanceof org.cidarlab.eugene.dom.Part) {

							NamedElement part = gate;

							String p_direction = "+";

							Part p = new Part(part_library.get_ALL_PARTS().get(
									part.getName()));

							p.set_direction(p_direction);

							module.add(p);
						}

						else if (gate instanceof org.cidarlab.eugene.dom.Device) {

							String gate_name = gate.getName();

							String g_direction = "+";

							String o = ((Device) circuit).getOrientations(
									g_index).toString();

							if (o.equals("[REVERSE]")) {
								g_direction = "-";
								Device reverse_gate = DeviceUtils
										.flipAndInvert((Device) gate);
								gate = reverse_gate;
							}

							String egate = g_direction + gate_name;

							ArrayList<Part> txn_unit = new ArrayList<Part>();

							int p_index = 0;

							for (NamedElement part : ((Device) gate)
									.getComponentList()) {

								String part_name = part.getName();

								String p_direction = "+";

								String op = ((Device) gate).getOrientations(
										p_index).toString();

								if (op.equals("[REVERSE]")) {
									p_direction = "-";
								}

								Part p = new Part(part_library.get_ALL_PARTS()
										.get(part.getName()));

								p.set_direction(p_direction);

								txn_unit.add(p);

								p_index++;

							}

							module.addAll(txn_unit);

						}

						g_index++;

					}

					module_variants.add(module);

				}

			}

			logger.info("Number of Eugene solutions "
					+ variants.getElements().size());

		} catch (EugeneException exception) {
			exception.printStackTrace();
		}

	}

	/**
	 * Infer the Device names from a rule by all tokens that are not Eugene
	 * keywords
	 *
	 * @param rule
	 * @return
	 */
	public ArrayList<String> getDeviceNamesFromRule(String rule) {

		ArrayList<String> keywords = new ArrayList<String>();

		// counting
		keywords.add("CONTAINS");
		keywords.add("NOTCONTAINS");
		keywords.add("EXACTLY");
		keywords.add("NOTEXACTLY");
		keywords.add("MORETHAN");
		keywords.add("NOTMORETHAN");
		keywords.add("SAME_COUNT");
		keywords.add("WITH");
		keywords.add("NOTWITH");
		keywords.add("THEN");

		// positioning
		keywords.add("STARTSWITH");
		keywords.add("ENDSWITH");
		keywords.add("AFTER");
		keywords.add("ALL_AFTER");
		keywords.add("SOME_AFTER");
		keywords.add("BEFORE");
		keywords.add("ALL_BEFORE");
		keywords.add("SOME_BEFORE");
		keywords.add("NEXTTO");
		keywords.add("ALL_NEXTTO");
		keywords.add("SOME_NEXTTO");

		// pairing
		keywords.add("EQUALS");
		keywords.add("NOTEQUALS");

		// orientation
		keywords.add("ALL_FORWARD");
		keywords.add("ALL_REVERSE");
		keywords.add("FORWARD");
		keywords.add("ALL_FORWARD");
		keywords.add("REVERSE");
		keywords.add("ALL_REVERSE");
		keywords.add("SAME_ORIENTATION");
		keywords.add("ALL_SAME_ORIENTATION");
		keywords.add("ALTERNATE_ORIENTATION");

		// interaction
		keywords.add("REPRESSES");
		keywords.add("INDUCES");
		keywords.add("DRIVES");

		// logic
		keywords.add("NOT");
		keywords.add("AND");
		keywords.add("OR");

		ArrayList<String> device_names = new ArrayList<String>();

		ArrayList<String> tokens = Util.lineTokenizer(rule);
		for (String token : tokens) {

			boolean is_keyword = false;

			for (String keyword : keywords) {

				if (token.equalsIgnoreCase(keyword)) {

					is_keyword = true;

					break;
				}
			}

			if (is_keyword == false
					&& token.substring(0, 1).matches("[a-z,A-Z]")) {

				device_names.add(token);

			}

		}

		return device_names;
	}

	/**
	 * String builder generates a .eug file from a LogicCircuit and writes the
	 * file to disk
	 *
	 * @param gates
	 * @param filename
	 * @param part_library
	 * @param options
	 * @return
	 */
	public String generateEugeneFile(ArrayList<Gate> gates, String filename,
			PartLibrary part_library, Args options) {

		if (options.is_eugene_scars()) {
			part_library.set_scars();
		}

		// this is the Eugene file String that will be built
		String eug = "";

		// only define each part type once
		HashSet<String> part_type_set = new HashSet<String>();

		// only define each part once (multiple instances might exist in the
		// case of promtoer fan-out)
		HashSet<String> part_set = new HashSet<String>();

		// map used instead of ArrayList of ArrayLists to save each txn unit
		HashMap<String, ArrayList<Part>> txn_units = new HashMap<>();

		for (Gate g : gates) {

            if(g.regulator == null || g.regulator.isEmpty()) {
                g.regulator = g.name;
            }

			for (int i = 0; i < g.get_txn_units().size(); ++i) {

                String key = "";
				if (!txn_units.containsKey(g.regulator)) {
                    key = g.regulator;

				} else {
                    key = g.regulator + "_" + (i + 1);
				}
                txn_units.put(key, g.get_txn_units().get(i));
			}
		}


		for (ArrayList<Part> txn_unit : txn_units.values()) {

			// set the part types and the parts

			if (options.is_eugene_dnaseq()) {
				for (Part p : txn_unit) {
					part_type_set.add(p.get_type());
					part_set.add(p.get_type() + " " + p.get_name()
							+ "(.SEQUENCE(\"" + p.get_seq() + "\"));\n");
				}
			}

			else {
				for (Part p : txn_unit) {
					part_type_set.add(p.get_type());
					part_set.add(p.get_type() + " " + p.get_name() + ";\n");
				}
			}
		}

		// include scars as parts and a part type

		ArrayList<Part> scars = new ArrayList<Part>();
		if (options.is_eugene_scars()) {
			part_type_set.add("scar");

			for (int i = 0; i < txn_units.size(); ++i) {
				// scars.add(get_SCARS().get(i));
				scars.add(part_library.get_scars().get(i));
			}

			// module end scar
			// scars.add(get_SCARS().get(get_SCARS().size()-1));
			scars.add(part_library.get_scars().get(
					part_library.get_scars().size() - 1));

			for (Part p : scars) {

				if (options.is_eugene_dnaseq()) {
					part_set.add(p.get_type() + " " + p.get_name()
							+ "(.SEQUENCE(\"" + p.get_seq() + "\"));\n");
				} else {
					part_set.add(p.get_type() + " " + p.get_name() + ";\n");
				}
			}
		}

		// add part type definitions to eug String

		for (String part_type : part_type_set) {
			eug += "PartType " + part_type + ";\n";
		}

		eug += "\n";

		// add part definitions to eug String (sorted alphabetically)

		ArrayList<String> parts = new ArrayList<String>();
		for (String p : part_set) {
			parts.add(p);
		}
		Collections.sort(parts);
		for (String p : parts) {
			eug += p;
		}

		eug += "\n";

		// define each gate device

		for (String regulator : txn_units.keySet()) {
			ArrayList<Part> txn_unit = txn_units.get(regulator);

			eug += "Device " + regulator + "_device" + "(\n";
			int gi = 0;

			for (Part p : txn_unit) {
				if (gi != 0) {
					eug += ",\n";
				}

				if (p.get_type().equals("promoter")) {
					eug += "   " + p.get_type();
				} else {
					eug += "   " + p.get_name();
				}

				gi++;
			}

			eug += "\n);\n";
		}

		eug += "\n";

		// define rules for each gate device

		for (String regulator : txn_units.keySet()) {
			ArrayList<Part> txn_unit = txn_units.get(regulator);

			ArrayList<String> names_in_this_device = new ArrayList<String>();

			for (Part p : txn_unit) {
				names_in_this_device.add(p.get_name());
			}

			eug += "Rule " + regulator + "_rules " + "( ON " + regulator
					+ "_device" + ":\n";

			if (options.is_tpmodel()) {

				int pcount = 0;

				for (Part p : txn_unit) {
					if (p.get_type().equals("promoter")) {
						if (pcount == 0) {
							eug += "   CONTAINS " + p.get_name();
							pcount++;
						} else {
							eug += " AND \n   CONTAINS " + p.get_name();
							pcount++;
						}
					}
				}

				int[] porder = {0};
				for (Gate g : gates) {
					if (g.regulator.equals(regulator)) {
						porder = g.get_porder();
						String promoter1 = "";
						String promoter2 = "";
						if(porder.length >1 && pcount>1) {
							promoter1 = g.getChildren().get(porder[0]).get_regulable_promoter().get_name();
							promoter2 = g.getChildren().get(porder[1]).get_regulable_promoter().get_name();
							eug += " AND\n   " + promoter1 + " BEFORE " + promoter2;
						}
					}

				}

			}

			else {

				int pcount = 0;

				for (Part p : txn_unit) {
					if (p.get_type().equals("promoter")) {
						if (pcount == 0) {
							eug += "   CONTAINS " + p.get_name();
							pcount++;
						} else {
							eug += " AND \n   CONTAINS " + p.get_name();
							pcount++;
						}
					}
				}
			}


			eug += insertRulesFromUCF(names_in_this_device,
					get_eugene_part_rules());

			eug += " AND\n   ALL_FORWARD\n);\n";
		}

		eug += "\n\n";

		// design all gate device variants

		for (String regulator : txn_units.keySet()) {
			eug += String.format("%-15s", regulator + "_devices")
					+ " = product(" + regulator + "_device" + ");\n";
		}

		eug += "\n";

		// initialize gate device names that will be included in the circuit
		// device

		ArrayList<String> names_in_circuit_device = new ArrayList<String>();

		for (String regulator : txn_units.keySet()) {
			eug += "Device " + "gate_" + regulator + "();" + "\n";
			names_in_circuit_device.add("gate_" + regulator);
		}

		eug += "\n";

		// initialize the circuit device

		eug += "Device circuit();\n\n";

		// apply rules on circuit device (before specifying the circuit device)

		eug += "Rule allRules( ON circuit:\n";

		// exactly 1 rule for each gate device
		int gi = 0;
		for (String regulator : txn_units.keySet()) {

			if (gi == 0) {
				eug += "   " + String.format("%-12s", "gate_" + regulator)
						+ " EXACTLY 1";
			} else {
				eug += " AND \n" + "   "
						+ String.format("%-12s", "gate_" + regulator)
						+ " EXACTLY 1";
			}

			gi++;
		}

		// add other rules specified in the UCF, if any
		eug += insertRulesFromUCF(names_in_circuit_device,
				get_eugene_gate_rules());

		 eug += " AND \n" + "   ALL_FORWARD";

		// include scars in circuit device
		if (options.is_eugene_scars()) {
			for (Part scar : scars) {
				eug += " AND \n" + "   "
						+ String.format("%-12s", scar.get_name())
						+ " EXACTLY 1";
			}
			for (Part scar : scars) {
				eug += " AND \n" + "   FORWARD " + scar.get_name();
			}
			for (int i = 0; i < scars.size(); ++i) {
				eug += " AND \n" + "   [" + (i * 2) + "] EQUALS "
						+ scars.get(i).get_name();
			}
		}

		eug += "\n);";

		eug += "\n\n";

		// initialize master array of all results
		eug += "Array allResults;\n\n";

		// build nested For-loops of enumerated gate devices
		// the 'permute' function will be called in the inner-most loop, and
		// results will be appended to allResults
		int tu_counter = 0;
		for (String regulator : txn_units.keySet()) {
			tu_counter++;
			String index = "i" + Integer.toString(tu_counter);

			eug += "for(num "
					+ index
					+ "=0;  "
					+ String.format("%-28s", index + "<sizeof(" + regulator
							+ "_devices);") + index + "=" + index + "+1) {\n";
		}

		eug += "\n";

		tu_counter = 0;
		for (String regulator : txn_units.keySet()) {
			tu_counter++;
			String index = "i" + Integer.toString(tu_counter);

			// set each gate device from the prior enumeration ('product'
			// function above)
			eug += String.format("%-12s", "gate_" + regulator) + " = "
					+ regulator + "_devices[" + index + "];\n";
		}

		eug += "\n";

		// define the components of the circuit device... order/orientation will
		// be permuted.
		gi = 0;
		eug += "Device circuit(\n";
		for (String regulator : txn_units.keySet()) {

			if (gi == 0) {
				eug += "   " + "gate_" + regulator;
				gi++;
			} else {
				eug += ",\n   " + "gate_" + regulator;
			}
		}

		if (options.is_eugene_scars()) {
			for (Part scar : scars) {
				eug += ",\n   " + scar.get_name();
			}
		}

		eug += "\n);\n";

		eug += "\n";

		// permute order/orientation of gate devices within the circuit device
		eug += "result = permute(circuit);\n\n";

		/*
		 * eug += "if(sizeof(result) >500) {\n" +
		 * "  for(num ir=0; ir<500; ir=ir+1) {\n" +
		 * "      allResults = allResults + result[ir];\n" + "  }\n" + "}\n" +
		 * "else {\n" + "      allResults = allResults + result;\n" + "}\n\n";
		 */

		// append results to allResults
		eug += "allResults = allResults + result;\n\n";

		for (int i = 0; i < txn_units.size(); ++i) {
			eug += "}\n";
		}

		// eug += "\n\nprintln(sizeof(allResults));\n";

		logger.info(eug);

		// write the file to disk
		Util.fileWriter(options.get_output_directory() + filename, eug, false);

		return eug;

	}

	private String insertRulesFromUCF(ArrayList<String> names_in_this_device,
			ArrayList<String> all_rules) {

		String rules = "";

		if (all_rules == null) {
			return "";
		}

		for (String rule : all_rules) {

			ArrayList<String> names_in_rule = getDeviceNamesFromRule(rule);

			boolean all_matching = true;
			boolean inconsistent_rules = false;

			for (String name : names_in_rule) {

				if (!names_in_this_device.contains(name)) {
					all_matching = false;
					break;
				}

				// startswith is used to implement roadblocking. More than 1
				// startswith rule for a gate device will lead to invalid Eugene
				// rules
				if (rules.contains("STARTSWITH")
						|| rules.contains("startswith")) {
					if (rule.contains("STARTSWITH")
							|| rule.contains("startswith")) {
						logger.info("more than 1 STARTSWITH rules found, omitting rule: "
								+ rule);
						inconsistent_rules = true;
					}
				}

			}

			// all rules are joined using AND
			if (all_matching && !inconsistent_rules) {
				rules += " AND \n   " + rule;
			}

		}

		return rules;

	}

    @Getter
    @Setter
    private ArrayList<String> _eugene_part_rules = new ArrayList<String>();
    @Getter
    @Setter
    private ArrayList<String> _eugene_gate_rules = new ArrayList<String>();


    @Getter @Setter private String threadDependentLoggername;

    private Logger logger  = Logger.getLogger(getClass());
}
