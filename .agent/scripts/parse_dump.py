import xml.etree.ElementTree as ET

tree = ET.parse(".agent/scripts/window_dump.xml")

for node in tree.iter("node"):
    # check if this node contains an item
    children = list(node)
    title = None
    summary = None
    widget_type = None
    checked = None
    for sub in node.iter("node"):
        rid = sub.attrib.get("resource-id", "")
        if rid.endswith(":id/title"):
            title = sub.attrib.get("text", "")
        elif rid.endswith(":id/summary"):
            summary = sub.attrib.get("text", "")
        elif rid.endswith(":id/switch_widget"):
            widget_type = "SWITCH"
            checked = sub.attrib.get("checked", "")
        elif rid.endswith(":id/checkbox"):
            widget_type = "CHECKBOX"
            checked = sub.attrib.get("checked", "")
    if title:
        print(f"TITLE: {title}")
        print(f"  STATE: {widget_type}={checked}")
        print(f"  SUMMARY: {summary}")
        print("-" * 40)
