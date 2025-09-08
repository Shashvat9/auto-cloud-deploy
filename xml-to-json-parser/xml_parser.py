# xml_parser_v3.py
import xml.etree.ElementTree as ET
import json
import argparse
import sys


def parse_geometry(cell):
    """Parses the mxGeometry element to get bounding box info."""
    geo = cell.find("mxGeometry")
    if geo is None:
        return None
    try:
        return {
            "x": float(geo.get("x", 0)),
            "y": float(geo.get("y", 0)),
            "width": float(geo.get("width", 0)),
            "height": float(geo.get("height", 0)),
        }
    except (ValueError, TypeError):
        return None


def is_contained(child_geo, parent_geo):
    """Checks if the child's geometry is contained within the parent's."""
    if not child_geo or not parent_geo:
        return False
    return (
            child_geo["x"] >= parent_geo["x"] and
            child_geo["y"] >= parent_geo["y"] and
            (child_geo["x"] + child_geo["width"]) <= (parent_geo["x"] + parent_geo["width"]) and
            (child_geo["y"] + child_geo["height"]) <= (parent_geo["y"] + parent_geo["height"])
    )


def parse_drawio_xml_v3(xml_file_path):
    """
    Parses a Draw.io XML file and converts it into a hierarchical JSON object
    by analyzing the geometry of the elements.
    """
    try:
        tree = ET.parse(xml_file_path)
        root = tree.getroot()
    except (ET.ParseError, FileNotFoundError) as e:
        print(f"Error reading or parsing XML file: {e}", file=sys.stderr)
        return None

    graph_model_root = root.find(".//mxGraphModel/root")
    if graph_model_root is None:
        return None

    nodes = {}
    edges = []

    # Pass 1: Extract all vertices with their geometry and value
    for cell in graph_model_root.findall("mxCell"):
        if cell.get("vertex") == "1" and cell.get("value"):
            node_id = cell.get("id")
            geometry = parse_geometry(cell)
            if node_id and geometry:
                nodes[node_id] = {
                    "id": node_id,
                    "label": cell.get("value", "").strip().replace("<br>", " "),
                    "geometry": geometry,
                    "children": []  # To be populated later
                }

    # Pass 2: Determine parent-child relationships based on geometry
    all_node_ids = list(nodes.keys())
    for child_id in all_node_ids:
        potential_parents = []
        for parent_id in all_node_ids:
            if child_id == parent_id:
                continue

            if is_contained(nodes[child_id]["geometry"], nodes[parent_id]["geometry"]):
                # The area is used to find the tightest fitting parent
                parent_area = nodes[parent_id]["geometry"]["width"] * nodes[parent_id]["geometry"]["height"]
                potential_parents.append((parent_id, parent_area))

        if potential_parents:
            # The best parent is the one with the smallest area that contains the child
            best_parent_id = min(potential_parents, key=lambda item: item[1])[0]
            nodes[best_parent_id]["children"].append(nodes[child_id])

    # Build the final hierarchical tree. The root elements are those that were not added as a child to any other node.
    child_ids = set()
    for node_data in nodes.values():
        for child in node_data["children"]:
            child_ids.add(child["id"])

    hierarchical_tree = [data for node_id, data in nodes.items() if node_id not in child_ids]

    # Clean up geometry from final output as it's no longer needed
    def clean_tree(node_list):
        for node in node_list:
            del node["geometry"]
            if node["children"]:
                clean_tree(node["children"])

    clean_tree(hierarchical_tree)

    # Pass 3: Extract edges (connections)
    for cell in graph_model_root.findall("mxCell"):
        if cell.get("edge") == "1":
            source_id = cell.get("source")
            target_id = cell.get("target")
            if source_id in nodes and target_id in nodes:
                edges.append({
                    "source_id": source_id,
                    "source_label": nodes[source_id]["label"],
                    "target_id": target_id,
                    "target_label": nodes[target_id]["label"],
                })

    output_schema = {
        "schema_version": "3.0",
        "diagram_name": root.find("diagram").get("name", "Untitled"),
        "architecture": hierarchical_tree,
        "connections": edges
    }

    return json.dumps(output_schema, indent=2)


def main():
    parser = argparse.ArgumentParser(description="Convert Draw.io XML to a hierarchical JSON using geometry.")
    parser.add_argument("input_file", help="The path to the input Draw.io XML file.")
    args = parser.parse_args()

    instruction_json = parse_drawio_xml_v3(args.input_file)
    if instruction_json:
        print(instruction_json)
    else:
        sys.exit(1)


if __name__ == "__main__":
    main()
