import { useState, useEffect, useRef } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Badge } from "./ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./ui/tabs";
import { Network, Search, Maximize2, Filter, Download } from "lucide-react";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";

interface GraphNode {
  id: string;
  label: string;
  type: 'recipe' | 'ingredient' | 'nutrient' | 'category';
  x: number;
  y: number;
  connections: number;
}

interface GraphEdge {
  from: string;
  to: string;
  type: string;
}

export function KnowledgeGraph() {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [selectedNode, setSelectedNode] = useState<GraphNode | null>(null);
  const [filterType, setFilterType] = useState<string>("all");

  const [nodes] = useState<GraphNode[]>([
    { id: '1', label: 'Pasta Carbonara', type: 'recipe', x: 400, y: 200, connections: 8 },
    { id: '2', label: 'Eggs', type: 'ingredient', x: 250, y: 150, connections: 12 },
    { id: '3', label: 'Bacon', type: 'ingredient', x: 250, y: 250, connections: 6 },
    { id: '4', label: 'Parmesan', type: 'ingredient', x: 550, y: 150, connections: 9 },
    { id: '5', label: 'Protein', type: 'nutrient', x: 100, y: 100, connections: 24 },
    { id: '6', label: 'Italian', type: 'category', x: 550, y: 300, connections: 15 },
    { id: '7', label: 'Spaghetti', type: 'ingredient', x: 550, y: 250, connections: 7 },
    { id: '8', label: 'Calcium', type: 'nutrient', x: 700, y: 150, connections: 18 },
    { id: '9', label: 'Breakfast', type: 'category', x: 100, y: 250, connections: 11 },
    { id: '10', label: 'Omelette', type: 'recipe', x: 250, y: 350, connections: 5 },
  ]);

  const [edges] = useState<GraphEdge[]>([
    { from: '1', to: '2', type: 'contains' },
    { from: '1', to: '3', type: 'contains' },
    { from: '1', to: '4', type: 'contains' },
    { from: '1', to: '7', type: 'contains' },
    { from: '1', to: '6', type: 'category' },
    { from: '2', to: '5', type: 'provides' },
    { from: '4', to: '8', type: 'provides' },
    { from: '10', to: '2', type: 'contains' },
    { from: '10', to: '9', type: 'category' },
  ]);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Clear canvas
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    // Draw edges
    ctx.strokeStyle = '#cbd5e1';
    ctx.lineWidth = 1.5;
    edges.forEach(edge => {
      const fromNode = nodes.find(n => n.id === edge.from);
      const toNode = nodes.find(n => n.id === edge.to);
      if (fromNode && toNode) {
        ctx.beginPath();
        ctx.moveTo(fromNode.x, fromNode.y);
        ctx.lineTo(toNode.x, toNode.y);
        ctx.stroke();
      }
    });

    // Draw nodes
    nodes.forEach(node => {
      if (filterType !== 'all' && node.type !== filterType) return;

      const colors = {
        recipe: '#3b82f6',
        ingredient: '#10b981',
        nutrient: '#f59e0b',
        category: '#8b5cf6'
      };

      ctx.fillStyle = colors[node.type];
      ctx.beginPath();
      ctx.arc(node.x, node.y, 20, 0, Math.PI * 2);
      ctx.fill();

      // Highlight selected node
      if (selectedNode?.id === node.id) {
        ctx.strokeStyle = '#1e293b';
        ctx.lineWidth = 3;
        ctx.stroke();
      }

      // Draw label
      ctx.fillStyle = '#1e293b';
      ctx.font = '12px sans-serif';
      ctx.textAlign = 'center';
      ctx.fillText(node.label, node.x, node.y + 35);
    });
  }, [nodes, edges, selectedNode, filterType]);

  const handleCanvasClick = (e: React.MouseEvent<HTMLCanvasElement>) => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const rect = canvas.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    const clickedNode = nodes.find(node => {
      const distance = Math.sqrt(Math.pow(node.x - x, 2) + Math.pow(node.y - y, 2));
      return distance <= 20;
    });

    setSelectedNode(clickedNode || null);
  };

  const entityStats = {
    recipes: nodes.filter(n => n.type === 'recipe').length,
    ingredients: nodes.filter(n => n.type === 'ingredient').length,
    nutrients: nodes.filter(n => n.type === 'nutrient').length,
    categories: nodes.filter(n => n.type === 'category').length,
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold text-gray-900">Knowledge Graph</h2>
          <p className="text-gray-600 mt-1">
            Visualize and explore relationships in your RecipeKG
          </p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" className="gap-2">
            <Maximize2 className="h-4 w-4" />
            Fullscreen
          </Button>
          <Button variant="outline" className="gap-2">
            <Download className="h-4 w-4" />
            Export
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card>
          <CardContent className="pt-6">
            <div className="text-center">
              <p className="text-sm text-gray-600">Recipes</p>
              <p className="text-2xl font-semibold text-blue-600 mt-1">{entityStats.recipes}</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-center">
              <p className="text-sm text-gray-600">Ingredients</p>
              <p className="text-2xl font-semibold text-green-600 mt-1">{entityStats.ingredients}</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-center">
              <p className="text-sm text-gray-600">Nutrients</p>
              <p className="text-2xl font-semibold text-orange-600 mt-1">{entityStats.nutrients}</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-center">
              <p className="text-sm text-gray-600">Categories</p>
              <p className="text-2xl font-semibold text-purple-600 mt-1">{entityStats.categories}</p>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Graph Visualization */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle>Graph Visualization</CardTitle>
              <div className="flex items-center gap-2">
                <Filter className="h-4 w-4 text-gray-500" />
                <Select value={filterType} onValueChange={setFilterType}>
                  <SelectTrigger className="w-[140px]">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All Types</SelectItem>
                    <SelectItem value="recipe">Recipes</SelectItem>
                    <SelectItem value="ingredient">Ingredients</SelectItem>
                    <SelectItem value="nutrient">Nutrients</SelectItem>
                    <SelectItem value="category">Categories</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
          </CardHeader>
          <CardContent>
            <div className="relative">
              <canvas
                ref={canvasRef}
                width={800}
                height={500}
                className="border border-gray-200 rounded-lg cursor-pointer w-full"
                onClick={handleCanvasClick}
              />
              <div className="absolute bottom-4 left-4 bg-white rounded-lg border border-gray-200 p-3 text-xs space-y-1">
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-blue-500"></div>
                  <span>Recipe</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-green-500"></div>
                  <span>Ingredient</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-orange-500"></div>
                  <span>Nutrient</span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-3 h-3 rounded-full bg-purple-500"></div>
                  <span>Category</span>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Node Details */}
        <Card>
          <CardHeader>
            <CardTitle>Node Details</CardTitle>
            <CardDescription>
              {selectedNode ? 'Selected entity information' : 'Click a node to view details'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {selectedNode ? (
              <div className="space-y-4">
                <div>
                  <Label className="text-xs text-gray-500">Entity Name</Label>
                  <p className="font-medium text-gray-900 mt-1">{selectedNode.label}</p>
                </div>
                <div>
                  <Label className="text-xs text-gray-500">Type</Label>
                  <Badge className="mt-1 capitalize">{selectedNode.type}</Badge>
                </div>
                <div>
                  <Label className="text-xs text-gray-500">Connections</Label>
                  <p className="font-medium text-gray-900 mt-1">{selectedNode.connections}</p>
                </div>
                <div>
                  <Label className="text-xs text-gray-500">Entity ID</Label>
                  <p className="font-mono text-xs text-gray-600 mt-1">{selectedNode.id}</p>
                </div>
                <div className="pt-4 border-t border-gray-200 space-y-2">
                  <Button size="sm" className="w-full">View Full Details</Button>
                  <Button size="sm" variant="outline" className="w-full">Edit Entity</Button>
                </div>
              </div>
            ) : (
              <div className="text-center py-8">
                <Network className="h-12 w-12 text-gray-300 mx-auto mb-3" />
                <p className="text-sm text-gray-500">Select a node to view details</p>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Entity Search */}
      <Card>
        <CardHeader>
          <CardTitle>Search Entities</CardTitle>
          <CardDescription>Find specific entities in your knowledge graph</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="relative mb-4">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
            <Input
              placeholder="Search for recipes, ingredients, nutrients..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10"
            />
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
            {nodes.filter(node => 
              node.label.toLowerCase().includes(searchQuery.toLowerCase())
            ).map(node => (
              <button
                key={node.id}
                onClick={() => setSelectedNode(node)}
                className={`p-3 border rounded-lg text-left hover:bg-gray-50 transition-colors ${
                  selectedNode?.id === node.id ? 'border-blue-500 bg-blue-50' : 'border-gray-200'
                }`}
              >
                <div className="flex items-center gap-2 mb-1">
                  <div className={`w-2 h-2 rounded-full ${
                    node.type === 'recipe' ? 'bg-blue-500' :
                    node.type === 'ingredient' ? 'bg-green-500' :
                    node.type === 'nutrient' ? 'bg-orange-500' :
                    'bg-purple-500'
                  }`}></div>
                  <span className="text-sm font-medium text-gray-900">{node.label}</span>
                </div>
                <p className="text-xs text-gray-500 capitalize">{node.type}</p>
              </button>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function Label({ className, children }: { className?: string; children: React.ReactNode }) {
  return <label className={className}>{children}</label>;
}
