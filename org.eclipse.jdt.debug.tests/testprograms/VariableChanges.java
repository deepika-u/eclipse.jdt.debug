/**********************************************************************
Copyright (c) 2000, 2002 IBM Corp.  All rights reserved.
This file is made available under the terms of the Common Public License v1.0
which accompanies this distribution, and is available at
http://www.eclipse.org/legal/cpl-v10.html
**********************************************************************/

public class VariableChanges {

		public static void main(String[] args) {
			new VariableChanges().test();
		}

		int count= 0;
		private void test() {
			int i = 0;
			count++;
			i++;
			count++;
			i++;
			count++;
		}
}
