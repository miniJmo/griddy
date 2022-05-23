package com.company.parser;

/* All AST nodes must implement this interface.  It provides basic
   machinery for constructing the parent and child relationships
   between nodes. */

public interface Node {
  Object jjtGetValue();

  Node[] getChildren();

  /** This method is called after the node has been made the current
    node.  It indicates that child nodes can now be added to it. */
  void jjtOpen();

  /** This method is called after all the child nodes have been
    added. */
  void jjtClose();

  /** This pair of methods are used to inform the node of its
    parent. */
  void jjtSetParent(Node n);
  Node getParent();

  /** This method tells the node to add its argument to the node's
    list of children.  */
  void jjtAddChild(Node n, int i);

  /** This method returns a child node.  The children are numbered
     from zero, left to right. */
  Node jjtGetChild(int i);

  /** Return the number of children the node has. */
  int getNumChildren();

  int getId();

  /** Accept the visitor. **/
  StringBuilder jjtAccept(GriddyVisitor visitor, StringBuilder data);
}