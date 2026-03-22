import { useState } from "react";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "./ui/card";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Badge } from "./ui/badge";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from "./ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { Database, Plus, ExternalLink, CheckCircle2, AlertCircle, Clock, Search } from "lucide-react";
import { toast } from "sonner";

interface DataSource {
  id: number;
  name: string;
  type: string;
  status: 'active' | 'inactive' | 'pending';
  entities: number;
  lastSync: string;
  apiUrl?: string;
}

export function DataSources() {
  const [searchQuery, setSearchQuery] = useState("");
  const [dataSources, setDataSources] = useState<DataSource[]>([
    { id: 1, name: 'USDA FoodData Central', type: 'API', status: 'active', entities: 3421, lastSync: '2 hours ago', apiUrl: 'https://api.nal.usda.gov/fdc/v1' },
    { id: 2, name: 'Open Food Facts', type: 'API', status: 'active', entities: 2103, lastSync: '5 hours ago', apiUrl: 'https://world.openfoodfacts.org/api/v0' },
    { id: 3, name: 'Spoonacular API', type: 'API', status: 'active', entities: 1842, lastSync: '1 day ago', apiUrl: 'https://api.spoonacular.com' },
    { id: 4, name: 'Recipe1M+ Dataset', type: 'Dataset', status: 'pending', entities: 0, lastSync: 'Never' },
    { id: 5, name: 'Edamam Recipe API', type: 'API', status: 'active', entities: 756, lastSync: '3 hours ago', apiUrl: 'https://api.edamam.com' },
    { id: 6, name: 'TheMealDB', type: 'API', status: 'inactive', entities: 543, lastSync: '2 weeks ago', apiUrl: 'https://www.themealdb.com/api/json/v1/1' },
  ]);

  const [newSource, setNewSource] = useState({ name: '', type: 'API', apiUrl: '', apiKey: '' });

  const handleAddDataSource = () => {
    if (newSource.name && newSource.type) {
      const newId = Math.max(...dataSources.map(ds => ds.id)) + 1;
      setDataSources([...dataSources, {
        id: newId,
        name: newSource.name,
        type: newSource.type,
        status: 'pending',
        entities: 0,
        lastSync: 'Never',
        apiUrl: newSource.apiUrl
      }]);
      setNewSource({ name: '', type: 'API', apiUrl: '', apiKey: '' });
      toast.success(`Data source "${newSource.name}" added successfully!`);
    }
  };

  const filteredSources = dataSources.filter(source => 
    source.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    source.type.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'active':
        return <CheckCircle2 className="h-4 w-4 text-green-600" />;
      case 'inactive':
        return <AlertCircle className="h-4 w-4 text-gray-400" />;
      case 'pending':
        return <Clock className="h-4 w-4 text-orange-500" />;
      default:
        return null;
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h2 className="text-2xl font-semibold text-gray-900">Data Sources</h2>
          <p className="text-gray-600 mt-1">
            Manage and configure food data sources for RecipeKG integration
          </p>
        </div>
        <Dialog>
          <DialogTrigger asChild>
            <Button className="gap-2">
              <Plus className="h-4 w-4" />
              Add Data Source
            </Button>
          </DialogTrigger>
          <DialogContent className="sm:max-w-[500px]">
            <DialogHeader>
              <DialogTitle>Add New Data Source</DialogTitle>
              <DialogDescription>
                Connect a new food data source to extend your RecipeKG
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="source-name">Data Source Name</Label>
                <Input
                  id="source-name"
                  placeholder="e.g., MyFoodDB API"
                  value={newSource.name}
                  onChange={(e) => setNewSource({ ...newSource, name: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="source-type">Source Type</Label>
                <Select value={newSource.type} onValueChange={(value) => setNewSource({ ...newSource, type: value })}>
                  <SelectTrigger id="source-type">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="API">API</SelectItem>
                    <SelectItem value="Dataset">Dataset</SelectItem>
                    <SelectItem value="Database">Database</SelectItem>
                    <SelectItem value="CSV">CSV File</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="api-url">API URL / Endpoint</Label>
                <Input
                  id="api-url"
                  placeholder="https://api.example.com/v1"
                  value={newSource.apiUrl}
                  onChange={(e) => setNewSource({ ...newSource, apiUrl: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="api-key">API Key (Optional)</Label>
                <Input
                  id="api-key"
                  type="password"
                  placeholder="YOUR_API_KEY_HERE"
                  value={newSource.apiKey}
                  onChange={(e) => setNewSource({ ...newSource, apiKey: e.target.value })}
                />
              </div>
            </div>
            <div className="flex gap-2">
              <Button className="flex-1" onClick={handleAddDataSource}>Add Source</Button>
              <DialogTrigger asChild>
                <Button variant="outline" className="flex-1">Cancel</Button>
              </DialogTrigger>
            </div>
          </DialogContent>
        </Dialog>
      </div>

      {/* Search */}
      <div className="relative">
        <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-gray-400" />
        <Input
          placeholder="Search data sources..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="pl-10"
        />
      </div>

      {/* Data Sources Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {filteredSources.map((source) => (
          <Card key={source.id} className="hover:shadow-md transition-shadow">
            <CardHeader>
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-2">
                  <Database className="h-5 w-5 text-blue-600" />
                  <CardTitle className="text-base">{source.name}</CardTitle>
                </div>
                {getStatusIcon(source.status)}
              </div>
              <CardDescription>{source.type}</CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <p className="text-gray-500">Status</p>
                  <Badge variant={source.status === 'active' ? 'default' : source.status === 'pending' ? 'secondary' : 'outline'} className="mt-1">
                    {source.status}
                  </Badge>
                </div>
                <div>
                  <p className="text-gray-500">Entities</p>
                  <p className="font-medium text-gray-900 mt-1">{source.entities.toLocaleString()}</p>
                </div>
              </div>
              <div>
                <p className="text-xs text-gray-500">Last Sync</p>
                <p className="text-sm text-gray-900 mt-1">{source.lastSync}</p>
              </div>
              {source.apiUrl && (
                <div className="pt-2 border-t border-gray-200">
                  <a
                    href={source.apiUrl}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="text-xs text-blue-600 hover:text-blue-700 flex items-center gap-1"
                  >
                    View API Docs
                    <ExternalLink className="h-3 w-3" />
                  </a>
                </div>
              )}
              <div className="flex gap-2">
                <Button size="sm" variant="outline" className="flex-1">
                  Configure
                </Button>
                <Button size="sm" variant="outline" className="flex-1">
                  Sync Now
                </Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      {filteredSources.length === 0 && (
        <Card>
          <CardContent className="py-12 text-center">
            <Database className="h-12 w-12 text-gray-400 mx-auto mb-4" />
            <p className="text-gray-600">No data sources found matching your search.</p>
          </CardContent>
        </Card>
      )}
    </div>
  );
}
