
struct Pass
{
	char name;
	int id; 
};



int main() 
{
	struct Pass pass1;
	pass1.name = 'A';
	pass1.id = 1;
	//struct Pass pass2;
	//pass2 = pass1;
  	if (pass1.name == 'b') {
    		ERROR: return;
  	}
	return 0;
}


